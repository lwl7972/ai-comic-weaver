package com.aicomic.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * 使用 Caffeine 作为本地缓存实现
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置 Caffeine Cache Manager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 配置各个缓存的参数
        cacheManager.registerCustomCache("projects",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(100)
                        .build());

        cacheManager.registerCustomCache("characters",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());

        cacheManager.registerCustomCache("scenes",
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build());

        cacheManager.registerCustomCache("episodes",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build());

        cacheManager.registerCustomCache("templates",
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(50)
                        .build());

        cacheManager.registerCustomCache("modelConfigs",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(20)
                        .build());

        return cacheManager;
    }
}
