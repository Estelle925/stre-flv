package com.stream.controller;

import com.stream.model.FlvConfig;
import com.stream.service.StreamService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 流媒体控制器
 * 处理流媒体相关的HTTP请求
 *
 * @author 海明
 * @since 1.0
 */
@RestController
public class StreamController {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(StreamController.class);
    
    @Resource
    private StreamService streamService;

    /**
     * 处理FLV流请求
     * 将RTSP流转换为FLV流并返回给客户端
     *
     * @param filename 请求的文件名，例如：camera1.flv
     * @param response HTTP响应对象，用于写入FLV流数据
     * @throws IOException 当IO操作发生异常时抛出
     */
    @GetMapping("/live/{filename}")
    public void streamFlv(@PathVariable String filename, HttpServletResponse response) throws IOException {
        try {
            response.setContentType("video/x-flv");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("Cache-Control", "no-cache");
            
            streamService.convertRtspToFlv(filename, response.getOutputStream());
        } catch (IllegalArgumentException e) {
            LOGGER.error("处理视频流失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("未找到对应的视频流配置");
        } catch (Exception e) {
            LOGGER.error("处理视频流时发生异常", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("处理视频流时发生错误");
        }
    }

    /**
     * 获取所有可用的FLV流链接
     *
     * @return FLV流链接列表
     */
    @GetMapping("/streams")
    public List<String> getAllStreams() {
        return streamService.getAllFlvLinks();
    }

    /**
     * 添加新的RTSP流配置
     */
    @PostMapping("/streams")
    public void addStream(@RequestBody FlvConfig streamConfig) {
        streamService.addStreamConfig(streamConfig.getFlvPath(), streamConfig.getRtspUrl());
    }

    /**
     * 获取所有流配置信息
     */
    @GetMapping("/streams/config")
    public List<FlvConfig> getAllStreamConfigs() {
        return streamService.getAllStreamConfigs();
    }

    /**
     * 删除流配置
     */
    @DeleteMapping("/streams/{flvPath}")
    public void deleteStream(@PathVariable String flvPath) {
        streamService.deleteStreamConfig(flvPath);
    }
} 
