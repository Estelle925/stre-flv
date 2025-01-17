package com.stream.model;

import lombok.Data;

/**
 * 流配置实体类
 *
 * @author 海明
 * @since 1.0
 */
@Data
public class FlvConfig {
    /**
     * FLV访问路径
     */
    private String flvPath;
    
    /**
     * RTSP源地址
     */
    private String rtspUrl;
} 
