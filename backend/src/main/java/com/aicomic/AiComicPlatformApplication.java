package com.aicomic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.File;

/**
 * AI 漫剧制作平台 — 主启动类
 * <p>
 * 启动优化策略：
 * <ul>
 *   <li>排除 JMX 自动配置（桌面应用不需要 JMX 监控）</li>
 *   <li>排除 WebSocket 自动配置（当前未使用 WebSocket）</li>
 *   <li>启用懒加载 + 异步支持</li>
 *   <li>数据目录在 Spring 启动前解析完毕</li>
 * </ul>
 */
@SpringBootApplication(exclude = {
        JmxAutoConfiguration.class,
        WebSocketServletAutoConfiguration.class,
})
@EnableConfigurationProperties
@EnableAsync
public class AiComicPlatformApplication {

    public static void main(String[] args) {
        // ============================================================
        // JVM 预热提示（不会阻塞启动）
        // ============================================================
        System.setProperty("java.awt.headless", "true");
        // 跳过 DNS 缓存 TTL 查找（本地桌面应用）
        System.setProperty("networkaddress.cache.ttl", "0");
        // 减少安全随机数阻塞（Linux 特有问题，Windows 无影响但无害）
        System.setProperty("java.security.egd", "file:/dev/./urandom");

        // ============================================================
        // 在 Spring 启动前解析 --data-dir 参数并创建目录
        // ============================================================
        String dataDir = resolveDataDir(args);
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        System.setProperty("app.data.dir", dir.getAbsolutePath());

        // ============================================================
        // 智能 DDL 策略：首次启动用 update 创建表，后续用 validate 加速
        // ============================================================
        String dbPath = System.getProperty("app.data.dir", "./data") + File.separator + "aicomic.db";
        File dbFile = new File(dbPath);
        boolean isFirstRun = !dbFile.exists() || dbFile.length() == 0;
        if (isFirstRun) {
            System.setProperty("spring.jpa.hibernate.ddl-auto", "update");
            System.out.println("[Init] First run detected, using ddl-auto=update to create tables");
        } else {
            System.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        }

        // ============================================================
        // 启动 Spring Boot
        // ============================================================
        SpringApplication app = new SpringApplication(AiComicPlatformApplication.class);
        app.run(args);
    }

    private static String resolveDataDir(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--data-dir=")) {
                return arg.substring("--data-dir=".length());
            }
        }
        return "./data";
    }
}
