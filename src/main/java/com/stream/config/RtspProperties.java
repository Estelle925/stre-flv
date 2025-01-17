package com.stream.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * RTSP配置类
 *
 * @author 海明
 * @since 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "rtsp")
public class RtspProperties {
    /**
     * RTSP流媒体地址
     */
    private Map<String, String> streams = new HashMap<>(16);
} 
