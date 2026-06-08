package com.aicomic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AI漫剧制作平台 - 后端主入口
 *
 * 启动方式：
 * 1. IDE 直接运行本类的 main 方法（开发模式，端口 8080）
 * 2. Electron spawn 子进程模式：java -jar --server.port={随机端口}
 */
@SpringBootApplication
@EnableConfigurationProperties
public class AiComicPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiComicPlatformApplication.class, args);
    }
}
