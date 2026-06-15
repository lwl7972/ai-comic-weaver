package com.aicomic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.io.File;

@SpringBootApplication
@EnableConfigurationProperties
public class AiComicPlatformApplication {

    public static void main(String[] args) {
        // 在 Spring 启动前解析 --data-dir 参数并创建目录
        String dataDir = resolveDataDir(args);
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        System.setProperty("app.data.dir", dir.getAbsolutePath());

        SpringApplication.run(AiComicPlatformApplication.class, args);
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
