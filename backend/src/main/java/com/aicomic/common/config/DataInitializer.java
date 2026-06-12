package com.aicomic.common.config;

import com.aicomic.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 应用启动时自动初始化预置数据
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TemplateService templateService;

    @Override
    public void run(String... args) {
        log.info("初始化预置数据...");
        try {
            templateService.initBuiltinTemplates();
            log.info("预置数据初始化完成");
        } catch (Exception e) {
            log.error("预置数据初始化失败: {}", e.getMessage(), e);
        }
    }
}
