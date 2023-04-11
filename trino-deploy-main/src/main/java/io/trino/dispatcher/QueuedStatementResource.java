package io.trino.dispatcher;

import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.trino.client.QueryError;
import io.trino.client.QueryResults;
import io.trino.execution.ExecutionFailureInfo;
import io.trino.execution.QueryManagerConfig;
import io.trino.server.HttpRequestSessionContextFactory;
import io.trino.server.ProtocolConfig;
import io.trino.server.ServerConfig;
import io.trino.server.SessionContext;
import io.trino.server.protocol.QueryInfoUrlFactory;
import io.trino.server.protocol.Slug;
import io.trino.spi.ErrorCode;
import io.trino.spi.QueryId;
import io.trino.spi.security.Identity;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.*;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.clearspring.analytics.util.Preconditions.checkState;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.util.concurrent.Futures.nonCancellationPropagating;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.trino.server.HttpRequestSessionContextFactory.AUTHENTICATED_IDENTITY;
import static io.trino.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Path("/v1/statement")
public class QueuedStatementResource {

    private static final Logger log = Logger.get(QueuedStatementResource.class);
    private static final Duration MAX_WAIT_TIME = new Duration(1, SECONDS);
    private static final Ordering<Comparable<Duration>> WAIT_ORDERING = Ordering.natural().nullsLast();
    private static final Duration NO_DURATION = new Duration(0, MILLISECONDS);

    private final HttpRequestSessionContextFactory sessionContextFactory;
    private final DispatchManager dispatchManager;

    private final QueryInfoUrlFactory queryInfoUrlFactory;

    private final Executor responseExecutor;
    private final ScheduledExecutorService timeoutExecutor;

    private final boolean compressionEnabled;
    private final Optional<String> alternateHeaderName;
    private final QueryManager queryManager;

    @Inject
    public QueuedStatementResource(
            HttpRequestSessionContextFactory sessionContextFactory,
            DispatchManager dispatchManager,
            DispatchExecutor executor,
            QueryInfoUrlFactory queryInfoUrlTemplate,
            ServerConfig serverConfig,
            ProtocolConfig protocolConfig,
            QueryManagerConfig queryManagerConfig)
    {
        this.sessionContextFactory = requireNonNull(sessionContextFactory, "sessionContextFactory is null");
        this.dispatchManager = requireNonNull(dispatchManager, "dispatchManager is null");
        this.responseExecutor = requireNonNull(executor, "executor is null").getExecutor();
        this.timeoutExecutor = requireNonNull(executor, "executor is null").getScheduledExecutor();
        this.queryInfoUrlFactory = requireNonNull(queryInfoUrlTemplate, "queryInfoUrlTemplate is null");
        this.compressionEnabled = requireNonNull(serverConfig, "serverConfig is null").isQueryResultsCompressionEnabled();
        this.alternateHeaderName = requireNonNull(protocolConfig, "protocolConfig is null").getAlternateHeaderName();

        requireNonNull(queryManagerConfig, "queryManagerConfig is null");
        queryManager = new QueryManager(queryManagerConfig.getClientTimeout());
    }


    @PostConstruct
    public void start()
    {
        queryManager.initialize(dispatchManager);
    }

    @PreDestroy
    public void stop()
    {
        queryManager.destroy();
    }

    @ResourceSecurity(AUTHENTICATED_USER)
    @POST
    @Produces(APPLICATION_JSON)
    public Response postStatement(
            String statement,
            @Context HttpServletRequest servletRequest,
            @Context HttpHeaders httpHeaders,
            @Context UriInfo uriInfo)
    {
        if (isNullOrEmpty(statement)) {
            throw badRequest(BAD_REQUEST, "SQL statement is empty");
        }

        Query query = registerQuery(statement, servletRequest, httpHeaders);

        return createQueryResultsResponse(query.getQueryResults(query.getLastToken(), uriInfo));
    }

    private Query registerQuery(String statement, HttpServletRequest servletRequest, HttpHeaders httpHeaders)
    {
        Optional<String> remoteAddress = Optional.ofNullable(servletRequest.getRemoteAddr());
        Optional<Identity> identity = Optional.ofNullable((Identity) servletRequest.getAttribute(AUTHENTICATED_IDENTITY));
        MultivaluedMap<String, String> headers = httpHeaders.getRequestHeaders();

        SessionContext sessionContext = sessionContextFactory.createSessionContext(headers, alternateHeaderName, remoteAddress, identity);
        Query query = new Query(statement, sessionContext, dispatchManager, queryInfoUrlFactory);
        queryManager.registerQuery(query);

        // let authentication filter know that identity lifecycle has been handed off
        servletRequest.setAttribute(AUTHENTICATED_IDENTITY, null);

        return query;
    }

    private static final class Query
    {
        private final String query;
        private final SessionContext sessionContext;
        private final DispatchManager dispatchManager;
        private final QueryId queryId;
        private final Optional<URI> queryInfoUrl;
        private final Slug slug = Slug.createNew();
        private final AtomicLong lastToken = new AtomicLong();

        private final long initTime = System.nanoTime();
        private final AtomicReference<Boolean> submissionGate = new AtomicReference<>();
        private final SettableFuture<Void> creationFuture = SettableFuture.create();

        public Query(String query, SessionContext sessionContext, DispatchManager dispatchManager, QueryInfoUrlFactory queryInfoUrlFactory)
        {
            this.query = requireNonNull(query, "query is null");
            this.sessionContext = requireNonNull(sessionContext, "sessionContext is null");
            this.dispatchManager = requireNonNull(dispatchManager, "dispatchManager is null");
            this.queryId = dispatchManager.createQueryId();
            requireNonNull(queryInfoUrlFactory, "queryInfoUrlFactory is null");
            this.queryInfoUrl = queryInfoUrlFactory.getQueryInfoUrl(queryId);
        }

        public QueryId getQueryId()
        {
            return queryId;
        }

        public Slug getSlug()
        {
            return slug;
        }

        public long getLastToken()
        {
            return lastToken.get();
        }

        public boolean tryAbandonSubmissionWithTimeout(Duration querySubmissionTimeout)
        {
            return Duration.nanosSince(initTime).compareTo(querySubmissionTimeout) >= 0 && submissionGate.compareAndSet(null, false);
        }

        public boolean isSubmissionAbandoned()
        {
            return Boolean.FALSE.equals(submissionGate.get());
        }

        public boolean isCreated()
        {
            return creationFuture.isDone();
        }

        private ListenableFuture<Void> waitForDispatched()
        {
            submitIfNeeded();
            if (!creationFuture.isDone()) {
                return nonCancellationPropagating(creationFuture);
            }
            // otherwise, wait for the query to finish
            return dispatchManager.waitForDispatched(queryId);
        }

        private void submitIfNeeded()
        {
            if (submissionGate.compareAndSet(null, true)) {
                creationFuture.setFuture(dispatchManager.createQuery(queryId, slug, sessionContext, query));
            }
        }

        public QueryResults getQueryResults(long token, UriInfo uriInfo)
        {
            long lastToken = this.lastToken.get();
            // token should be the last token or the next token
            if (token != lastToken && token != lastToken + 1) {
                throw new WebApplicationException(Response.Status.GONE);
            }
            // advance (or stay at) the token
            this.lastToken.compareAndSet(lastToken, token);

            // if query submission has not finished, return simple empty result
            if (!creationFuture.isDone()) {
                return createQueryResults(
                        token + 1,
                        uriInfo,
                        DispatchInfo.queued(NO_DURATION, NO_DURATION));
            }

            Optional<DispatchInfo> dispatchInfo = dispatchManager.getDispatchInfo(queryId);
            if (dispatchInfo.isEmpty()) {
                // query should always be found, but it may have just been determined to be abandoned
                throw new WebApplicationException(Response
                        .status(NOT_FOUND)
                        .build());
            }

            return createQueryResults(token + 1, uriInfo, dispatchInfo.get());
        }

        public void cancel()
        {
            creationFuture.addListener(() -> dispatchManager.cancelQuery(queryId), directExecutor());
        }

        public void destroy()
        {
            sessionContext.getIdentity().destroy();
        }

        private QueryResults createQueryResults(long token, UriInfo uriInfo, DispatchInfo dispatchInfo)
        {
            URI nextUri = getNextUri(token, uriInfo, dispatchInfo);

            Optional<QueryError> queryError = dispatchInfo.getFailureInfo()
                    .map(this::toQueryError);

            return QueuedStatementResource.createQueryResults(
                    queryId,
                    nextUri,
                    queryError,
                    uriInfo,
                    queryInfoUrl,
                    dispatchInfo.getElapsedTime(),
                    dispatchInfo.getQueuedTime());
        }

        private URI getNextUri(long token, UriInfo uriInfo, DispatchInfo dispatchInfo)
        {
            // if failed, query is complete
            if (dispatchInfo.getFailureInfo().isPresent()) {
                return null;
            }
            // if dispatched, redirect to new uri
            return dispatchInfo.getCoordinatorLocation()
                    .map(coordinatorLocation -> getRedirectUri(coordinatorLocation, uriInfo))
                    .orElseGet(() -> getQueuedUri(queryId, slug, token, uriInfo));
        }

        private URI getRedirectUri(CoordinatorLocation coordinatorLocation, UriInfo uriInfo)
        {
            URI coordinatorUri = coordinatorLocation.getUri(uriInfo);
            return UriBuilder.fromUri(coordinatorUri)
                    .replacePath("/v1/statement/executing")
                    .path(queryId.toString())
                    .path(slug.makeSlug(EXECUTING_QUERY, 0))
                    .path("0")
                    .build();
        }

        private QueryError toQueryError(ExecutionFailureInfo executionFailureInfo)
        {
            ErrorCode errorCode;
            if (executionFailureInfo.getErrorCode() != null) {
                errorCode = executionFailureInfo.getErrorCode();
            }
            else {
                errorCode = GENERIC_INTERNAL_ERROR.toErrorCode();
                log.warn("Failed query %s has no error code", queryId);
            }

            return new QueryError(
                    firstNonNull(executionFailureInfo.getMessage(), "Internal error"),
                    null,
                    errorCode.getCode(),
                    errorCode.getName(),
                    errorCode.getType().toString(),
                    executionFailureInfo.getErrorLocation(),
                    executionFailureInfo.toFailureInfo());
        }
    }

    @ThreadSafe
    private static class QueryManager
    {
        private final ConcurrentMap<QueryId, Query> queries = new ConcurrentHashMap<>();
        private final ScheduledExecutorService scheduledExecutorService = newSingleThreadScheduledExecutor(daemonThreadsNamed("drain-state-query-manager"));

        private final Duration querySubmissionTimeout;

        public QueryManager(Duration querySubmissionTimeout)
        {
            this.querySubmissionTimeout = requireNonNull(querySubmissionTimeout, "querySubmissionTimeout is null");
        }

        public void initialize(DispatchManager dispatchManager)
        {
            scheduledExecutorService.scheduleWithFixedDelay(() -> syncWith(dispatchManager), 200, 200, MILLISECONDS);
        }

        public void destroy()
        {
            scheduledExecutorService.shutdownNow();
        }

        private void syncWith(DispatchManager dispatchManager)
        {
            queries.forEach((queryId, query) -> {
                if (shouldBePurged(dispatchManager, query)) {
                    removeQuery(queryId);
                }
            });
        }

        private boolean shouldBePurged(DispatchManager dispatchManager, Query query)
        {
            if (query.isSubmissionAbandoned()) {
                // Query submission was explicitly abandoned
                return true;
            }
            if (query.tryAbandonSubmissionWithTimeout(querySubmissionTimeout)) {
                // Query took too long to be submitted by the client
                return true;
            }
            if (query.isCreated() && !dispatchManager.isQueryRegistered(query.getQueryId())) {
                // Query was created in the DispatchManager, and DispatchManager has already purged the query
                return true;
            }
            return false;
        }

        private void removeQuery(QueryId queryId)
        {
            Optional.ofNullable(queries.remove(queryId))
                    .ifPresent(QueryManager::destroyQuietly);
        }

        private static void destroyQuietly(Query query)
        {
            try {
                query.destroy();
            }
            catch (Throwable t) {
                log.error(t, "Error destroying query");
            }
        }

        public void registerQuery(Query query)
        {
            Query existingQuery = queries.putIfAbsent(query.getQueryId(), query);
            checkState(existingQuery == null, "Query already registered");
        }

        @Nullable
        public Query getQuery(QueryId queryId)
        {
            return queries.get(queryId);
        }
    }
}
