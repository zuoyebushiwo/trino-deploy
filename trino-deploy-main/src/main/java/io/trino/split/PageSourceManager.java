/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.split;

import io.trino.Session;
import io.trino.connector.CatalogName;
import io.trino.metadata.Split;
import io.trino.metadata.TableHandle;
import io.trino.spi.connector.*;
import io.trino.spi.predicate.TupleDomain;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.trino.SystemSessionProperties.isAllowPushdownIntoConnectors;
import static java.util.Objects.requireNonNull;

public class PageSourceManager
        implements PageSourceProvider
{
    private final ConcurrentMap<CatalogName, ConnectorPageSourceProvider> pageSourceProviders = new ConcurrentHashMap<>();

    public void addConnectorPageSourceProvider(CatalogName catalogName, ConnectorPageSourceProvider pageSourceProvider)
    {
        requireNonNull(catalogName, "catalogName is null");
        requireNonNull(pageSourceProvider, "pageSourceProvider is null");
        checkState(pageSourceProviders.put(catalogName, pageSourceProvider) == null, "PageSourceProvider for connector '%s' is already registered", catalogName);
    }

    public void removeConnectorPageSourceProvider(CatalogName catalogName)
    {
        pageSourceProviders.remove(catalogName);
    }

    @Override
    public ConnectorPageSource createPageSource(Session session, Split split, TableHandle table, List<ColumnHandle> columns, DynamicFilter dynamicFilter)
    {
        requireNonNull(columns, "columns is null");
        checkArgument(split.getCatalogName().equals(table.getCatalogName()), "mismatched split and table");
        CatalogName catalogName = split.getCatalogName();

        ConnectorPageSourceProvider provider = getPageSourceProvider(catalogName);
        TupleDomain<ColumnHandle> constraint = dynamicFilter.getCurrentPredicate();
        if (constraint.isNone()) {
            return new EmptyPageSource();
        }
        if (!isAllowPushdownIntoConnectors(session)) {
            dynamicFilter = DynamicFilter.EMPTY;
        }
        return provider.createPageSource(
                table.getTransaction(),
                session.toConnectorSession(catalogName),
                split.getConnectorSplit(),
                table.getConnectorHandle(),
                columns,
                dynamicFilter);
    }

    private ConnectorPageSourceProvider getPageSourceProvider(CatalogName catalogName)
    {
        ConnectorPageSourceProvider provider = pageSourceProviders.get(catalogName);
        checkArgument(provider != null, "No page source provider for connector: %s", catalogName);
        return provider;
    }
}
