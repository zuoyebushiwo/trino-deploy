package com.zuoye.my.io.airlift;

import com.zuoye.my.io.airlift.resource.ServiceModule;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.event.client.EventModule;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;

/**
 * @author ZhangXueJun
 * @Date 2023年03月27日
 */
public class Service {

    public static void main(String[] args) {
        Bootstrap app = new Bootstrap(
                new ServiceModule(),
                new NodeModule(),
                new HttpServerModule(),
                new EventModule(),
                new JsonModule(),
                new JaxrsModule()
        );
        app.initialize();
    }
}
