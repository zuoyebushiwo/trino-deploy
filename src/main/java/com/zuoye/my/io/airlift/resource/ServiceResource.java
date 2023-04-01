package com.zuoye.my.io.airlift.resource;

import io.airlift.log.Logger;
import org.weakref.jmx.Managed;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ZuoYe
 * @Date 2023年03月27日
 */
@Path("/v1/service")
public class ServiceResource
{

    private final static Logger LOG = Logger.get(ServiceResource.class);
    private final ServiceConfig config;

    private final AtomicLong helloCount = new AtomicLong();

    @Inject
    public ServiceResource(ServiceConfig config) {
        this.config = config;
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String hello()
    {
        LOG.info("Formatted output %s", "xx");
        helloCount.incrementAndGet();
        return config.getHelloMessage();            // CHANGED
    }

    @Managed                           // NEW
    public long getHelloCount()        // NEW
    {
        return helloCount.get();       // NEW
    }
}
