package com.zuoye.my.io.airlift.resource;

import io.airlift.configuration.Config;

import javax.validation.constraints.NotBlank;

/**
 * @author ZhangXueJun
 * @Date 2023年03月28日
 */
public class ServiceConfig {

    private String helloMessage = "Hello Airlift!";

    @NotBlank
    public String getHelloMessage()
    {
        return helloMessage;
    }

    @Config("hello.message")
    public ServiceConfig setHelloMessage(String helloMessage)
    {
        this.helloMessage = helloMessage;
        return this;
    }
}
