package com.aicomic.common.config;

import com.aicomic.service.queue.VideoTaskQueueManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 视频队列管理器配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class VideoQueueConfig {

    private final VideoTaskQueueManager queueManager;

    /**
     * 应用启动时启动队列管理器
     */
    @PostConstruct
    public void start() {
        log.info("初始化视频生成队列管理器...");
        queueManager.start();
    }

    /**
     * 应用关闭时停止队列管理器
     */
    @PreDestroy
    public void shutdown() {
        log.info("关闭视频生成队列管理器...");
        queueManager.shutdown();
    }
}
