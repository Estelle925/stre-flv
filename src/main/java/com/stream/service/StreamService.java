package com.stream.service;

import com.stream.config.RtspProperties;
import com.stream.model.FlvConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流媒体转换服务类
 * 负责将RTSP流转换为FLV流
 *
 * @author 海明
 * @since 1.0
 */
@Slf4j
@Service
public class StreamService {

    @Resource
    private ThreadPoolTaskExecutor ffmpegThreadPool;
    
    @Resource
    private RtspProperties rtspProperties;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ConfigSyncService configSyncService;

    private static final String REDIS_STREAM_KEY_PREFIX = "rtsp:stream:";

    /**
     * 获取RTSP地址
     * 首先从配置文件获取，如果没有则从Redis获取
     *
     * @param flvPath FLV路径
     * @return RTSP地址
     */
    private String getRtspUrl(String flvPath) {
        log.info("正在查找FLV路径对应的RTSP地址: {}", flvPath);
        
        // 首先从配置文件中获取
        String rtspUrl = rtspProperties.getStreams().get(flvPath);
        if (rtspUrl != null) {
            log.info("从配置文件中找到RTSP地址: {}", rtspUrl);
            return rtspUrl;
        }
        
        // 如果配置文件中没有，则从Redis中获取
        String redisKey = REDIS_STREAM_KEY_PREFIX + flvPath;
        rtspUrl = stringRedisTemplate.opsForValue().get(redisKey);
        if (rtspUrl != null) {
            log.info("从Redis中找到RTSP地址: {}", rtspUrl);
            return rtspUrl;
        }
        
        log.error("未找到FLV路径对应的RTSP地址: {}", flvPath);
        return null;
    }

    /**
     * 将RTSP流转换为FLV流
     * 使用FFmpeg进行流转换，并将转换后的流写入输出流
     *
     * @param flvPath 请求的FLV路径
     * @param outputStream 输出流，用于写入转换后的FLV数据
     * @throws IOException 当IO操作发生异常时抛出
     * @throws IllegalArgumentException 当找不到对应的RTSP地址时抛出
     */
    public void convertRtspToFlv(String flvPath, OutputStream outputStream) throws IOException {
        String rtspUrl = getRtspUrl(flvPath);
        Process process = getProcess(rtspUrl);

        // 使用线程池处理FFmpeg的错误输出
        ffmpegThreadPool.execute(() -> {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    // 过滤掉一些不重要的FFmpeg输出信息
                    if (line.contains("Error") || 
                        line.contains("Failed") || 
                        line.contains("Invalid") || 
                        line.contains("Unable") ||
                        line.contains("Connection refused")) {
                        log.error("FFmpeg错误: {}", line);
                    } else if (line.contains("Stream") || line.contains("frame=")) {
                        log.debug("FFmpeg信息: {}", line);
                    }
                }
            } catch (IOException e) {
                log.error("读取FFmpeg错误输出时发生异常", e);
            }
        });

        // 将FFmpeg的输出流传输到响应输出流
        try {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = process.getInputStream().read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }
        } finally {
            process.destroy();
        }
    }

    private static Process getProcess(String rtspUrl) throws IOException {
        if (rtspUrl == null) {
            throw new IllegalArgumentException("未找到对应的RTSP地址");
        }

        ProcessBuilder builder = new ProcessBuilder(
            "ffmpeg",
            "-i", rtspUrl,
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-tune", "zerolatency",
            "-c:a", "aac",
            "-ar", "44100",
            "-f", "flv",
            "-flvflags", "no_duration_filesize",
            "-"
        );

        return builder.start();
    }

    /**
     * 获取所有可用的FLV流链接
     *
     * @return FLV流链接列表
     */
    public List<String> getAllFlvLinks() {

        // 从配置文件中获取
        Set<String> links = new HashSet<>(rtspProperties.getStreams().keySet());
        
        // 从Redis中获取
        Set<String> keys = stringRedisTemplate.keys(REDIS_STREAM_KEY_PREFIX + "*");
        links.addAll(keys.stream()
                .map(key -> key.substring(REDIS_STREAM_KEY_PREFIX.length()))
                .collect(Collectors.toSet()));

        return new ArrayList<>(links);
    }

    /**
     * 添加新的流配置
     */
    public void addStreamConfig(String flvPath, String rtspUrl) {
        if (flvPath == null || flvPath.trim().isEmpty()) {
            throw new IllegalArgumentException("FLV路径不能为空");
        }
        if (rtspUrl == null || rtspUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("RTSP地址不能为空");
        }
        
        // 规范化路径
        flvPath = flvPath.trim();
        rtspUrl = rtspUrl.trim();
        
        // 保存到Redis
        stringRedisTemplate.opsForValue().set(REDIS_STREAM_KEY_PREFIX + flvPath, rtspUrl);
        
        // 更新内存中的配置
        rtspProperties.getStreams().put(flvPath, rtspUrl);
        
        // 更新yml文件
        configSyncService.updateYmlFile(rtspProperties.getStreams());
        
        log.info("添加新的流配置成功 - FLV路径: {}, RTSP地址: {}", flvPath, rtspUrl);
    }

    /**
     * 获取所有流配置信息
     */
    public List<FlvConfig> getAllStreamConfigs() {
        List<FlvConfig> configs = new ArrayList<>();
        
        // 从配置文件中获取
        rtspProperties.getStreams().forEach((flvPath, rtspUrl) -> {
            FlvConfig config = new FlvConfig();
            config.setFlvPath(flvPath);
            config.setRtspUrl(rtspUrl);
            configs.add(config);
        });
        
        // 从Redis中获取
        Set<String> keys = stringRedisTemplate.keys(REDIS_STREAM_KEY_PREFIX + "*");
        keys.forEach(key -> {
            String flvPath = key.substring(REDIS_STREAM_KEY_PREFIX.length());
            String rtspUrl = stringRedisTemplate.opsForValue().get(key);
            // 如果配置文件中没有这个配置，才添加
            if (!rtspProperties.getStreams().containsKey(flvPath)) {
                FlvConfig config = new FlvConfig();
                config.setFlvPath(flvPath);
                config.setRtspUrl(rtspUrl);
                configs.add(config);
            }
        });

        return configs;
    }

    /**
     * 删除流配置
     */
    public void deleteStreamConfig(String flvPath) {
        // 从Redis中删除
        stringRedisTemplate.delete(REDIS_STREAM_KEY_PREFIX + flvPath);
        
        // 从内存配置中删除
        rtspProperties.getStreams().remove(flvPath);
        
        // 更新yml文件
        configSyncService.updateYmlFile(rtspProperties.getStreams());
        
        log.info("删除流配置成功 - FLV路径: {}", flvPath);
    }
} 
