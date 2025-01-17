package com.stream.service;

import com.stream.config.RtspProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置同步服务
 * 负责同步yml配置文件和Redis中的配置
 *
 * @author 海明
 * @since 1.0
 */
@Slf4j
@Service
public class ConfigSyncService {

    @Resource
    private RtspProperties rtspProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_STREAM_KEY_PREFIX = "rtsp:stream:";
    private static final String YML_PATH = "src/main/resources/application-dev.yml";

    /**
     * Redis配置信息
     */
    private static final class RedisConfig {
        static final String HOST = "112.124.27.167";
        static final int PORT = 6379;
        static final String PASSWORD = "ym@20240529ABC";
        static final int DATABASE = 6;
        static final int MAX_ACTIVE = 200;
        static final String MAX_WAIT = "-1ms";
        static final int MAX_IDLE = 10;
        static final int MIN_IDLE = 0;
    }

    /**
     * 应用启动时将yml配置同步到Redis
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncYmlToRedis() {
        log.info("开始同步YML配置到Redis...");
        try {
            Map<String, String> streams = rtspProperties.getStreams();
            if (streams != null && !streams.isEmpty()) {
                for (Map.Entry<String, String> entry : streams.entrySet()) {
                    String key = REDIS_STREAM_KEY_PREFIX + entry.getKey();
                    // 只有当Redis中不存在该key时才同步
                    if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
                        stringRedisTemplate.opsForValue().set(key, entry.getValue());
                        log.info("同步配置到Redis: {} -> {}", entry.getKey(), entry.getValue());
                    } else {
                        log.info("Redis中已存在配置: {}, 跳过同步", entry.getKey());
                    }
                }
            }
            log.info("YML配置同步到Redis完成");
        } catch (Exception e) {
            log.error("同步YML配置到Redis失败", e);
        }
    }

    /**
     * 更新yml文件
     * 合并Redis中的配置和yml中的配置
     */
    public void updateYmlFile(Map<String, String> newStreams) {
        try {
            // 读取现有的yml文件内容
            Path path = Paths.get(YML_PATH);
            if (!Files.exists(path)) {
                log.error("找不到yml文件: {}", YML_PATH);
                return;
            }

            // 合并Redis中的配置
            Map<String, String> mergedStreams = new HashMap<>(16);
            // 首先添加yml中的默认配置
            mergedStreams.putAll(rtspProperties.getStreams());
            // 然后添加新的配置，如果有相同的key会覆盖
            mergedStreams.putAll(newStreams);

            // 准备新的配置
            Map<String, Object> config = new HashMap<>(16);
            
            // 设置服务器配置
            config.put("server", createServerConfig());
            
            // 设置Spring配置
            config.put("spring", createSpringConfig());
            
            // 设置RTSP配置，使用合并后的配置
            Map<String, Object> rtsp = new HashMap<>(16);
            rtsp.put("streams", mergedStreams);
            config.put("rtsp", rtsp);

            // 配置Yaml输出格式
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);

            // 写入yml文件
            Yaml yaml = new Yaml(options);
            try (FileWriter writer = new FileWriter(path.toFile())) {
                yaml.dump(config, writer);
            }

            log.info("YML文件更新成功，当前共有{}个配置", mergedStreams.size());
        } catch (IOException e) {
            log.error("更新YML文件失败", e);
            throw new RuntimeException("更新配置文件失败", e);
        }
    }

    /**
     * 创建YAML实例
     */
    private Yaml createYamlInstance() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }

    /**
     * 构建配置Map
     */
    private Map<String, Object> buildConfigMap(Map<String, String> newStreams) {
        Map<String, Object> config = new HashMap<>(16);
        
        // 设置服务器配置
        config.put("server", createServerConfig());
        
        // 设置Spring配置
        config.put("spring", createSpringConfig());
        
        // 设置RTSP配置
        Map<String, Object> rtsp = new HashMap<>(16);
        rtsp.put("streams", newStreams);
        config.put("rtsp", rtsp);

        return config;
    }

    /**
     * 创建服务器配置
     */
    private Map<String, Object> createServerConfig() {
        Map<String, Object> server = new HashMap<>(16);
        server.put("port", 8080);
        return server;
    }

    /**
     * 创建Spring配置
     */
    private Map<String, Object> createSpringConfig() {
        Map<String, Object> spring = new HashMap<>(16);
        
        // 设置profiles
        Map<String, String> profiles = new HashMap<>(16);
        profiles.put("active", "dev");
        spring.put("profiles", profiles);
        
        // 设置Redis配置
        spring.put("redis", createRedisConfig());
        
        return spring;
    }

    /**
     * 创建Redis配置
     */
    private Map<String, Object> createRedisConfig() {
        Map<String, Object> redis = new HashMap<>(16);
        redis.put("host", RedisConfig.HOST);
        redis.put("port", RedisConfig.PORT);
        redis.put("password", RedisConfig.PASSWORD);
        redis.put("database", RedisConfig.DATABASE);

        // 设置连接池配置
        Map<String, Object> lettuce = new HashMap<>(16);
        Map<String, Object> pool = new HashMap<>(16);
        pool.put("max-active", RedisConfig.MAX_ACTIVE);
        pool.put("max-wait", RedisConfig.MAX_WAIT);
        pool.put("max-idle", RedisConfig.MAX_IDLE);
        pool.put("min-idle", RedisConfig.MIN_IDLE);
        lettuce.put("pool", pool);
        redis.put("lettuce", lettuce);

        return redis;
    }
} 
