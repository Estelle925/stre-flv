package com.stream.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置类
 * 用于处理FFmpeg流转换过程中的异步任务
 *
 * @author 海明
 * @since 1.0
 */
@Configuration
public class ThreadPoolConfig {
    
    /**
     * 创建FFmpeg处理专用线程池
     * 用于处理FFmpeg流转换过程中的错误输出和其他异步任务
     *
     * @return ThreadPoolTaskExecutor 线程池执行器
     */
    @Bean
    public ThreadPoolTaskExecutor ffmpegThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 队列容量
        executor.setQueueCapacity(20);
        // 线程名前缀
        executor.setThreadNamePrefix("ffmpeg-pool-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
} 
