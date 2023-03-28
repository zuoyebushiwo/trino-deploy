package com.zuoye.my.io.airlift.resource;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.configuration.ConfigBinder;
import io.airlift.jaxrs.JaxrsBinder;
import org.weakref.jmx.guice.ExportBinder;

/**
 * @author ZhangXueJun
 * @Date 2023年03月27日
 */
public class ServiceModule implements Module {
    @Override
    public void configure(Binder binder) {
        JaxrsBinder.jaxrsBinder(binder).bind(ServiceResource.class);
        ConfigBinder.configBinder(binder).bindConfig(ServiceConfig.class);
        ExportBinder.newExporter(binder).export(ServiceResource.class).withGeneratedName();
    }
}
