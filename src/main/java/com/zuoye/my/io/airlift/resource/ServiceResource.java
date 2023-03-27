package com.zuoye.my.io.airlift.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author ZhangXueJun
 * @Date 2023年03月27日
 */
@Path("/v1/service")
public class ServiceResource
{
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String hello()
    {
        return "Hello Airlift!";
    }
}
