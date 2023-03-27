package com.zuoye.my.io.airlift.resource;

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.jaxrs.JaxrsBinder;

/**
 * @author ZhangXueJun
 * @Date 2023年03月27日
 */
public class ServiceModule implements Module {
    @Override
    public void configure(Binder binder) {
        JaxrsBinder.jaxrsBinder(binder).bind(ServiceResource.class);
    }
}
