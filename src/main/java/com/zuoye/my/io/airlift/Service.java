package com.zuoye.my.io.airlift;

import com.google.inject.Injector;
import com.zuoye.my.io.airlift.resource.ServiceModule;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.event.client.EventModule;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.jmx.JmxHttpModule;
import io.airlift.jmx.JmxModule;
import io.airlift.jmx.http.rpc.JmxHttpRpcModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import org.weakref.jmx.guice.MBeanModule;

/**
 * @author ZuoYe
 * @Date 2023年03月27日
 */
public class Service {

    public static void main(String[] args) {
        Bootstrap app = new Bootstrap(
                new JmxModule(),            // NEW
                new JmxHttpModule(),        // NEW
                new JmxHttpRpcModule(),     // NEW
                new MBeanModule(),
                new ServiceModule(),
                new NodeModule(),
                new HttpServerModule(),
                new EventModule(),
                new JsonModule(),
                new JaxrsModule()
        );
        Injector injector = app.initialize();
    }
}
