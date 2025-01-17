package com.stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RTSP转FLV流媒体服务应用程序入口类
 *
 * @author 海明
 * @since 1.0
 */
@SpringBootApplication
public class StreamApplication {
    /**
     * 应用程序主入口方法
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(StreamApplication.class, args);
    }
} 
