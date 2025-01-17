package com.stream.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 流媒体服务配置类
 * 配置异步请求支持，用于处理长连接流媒体请求
 *
 * @author 海明
 * @since 1.0
 */
@Configuration
public class StreamConfig implements WebMvcConfigurer {
    
    /**
     * 配置异步请求支持
     * 设置异步请求超时时间为-1，表示永不超时
     *
     * @param configurer 异步支持配置器
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(-1);
    }
} 
