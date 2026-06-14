package com.aicomic.common.config;

import com.aicomic.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时自动初始化预置数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final TemplateService templateService;

    @Override
    public void run(String... args) {
        log.info("开始初始化项目模板...");
        try {
            long count = templateService.getTemplates(null).size();
            if (count > 0) {
                log.info("已存在 {} 个项目模板，跳过初始化", count);
                return;
            }
            templateService.initBuiltinTemplates();
            long afterCount = templateService.getTemplates(null).size();
            log.info("项目模板初始化完成，共 {} 个模板", afterCount);
        } catch (Exception e) {
            log.error("项目模板初始化失败: {}", e.getMessage(), e);
        }
    }
}
