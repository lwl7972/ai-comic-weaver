package com.aicomic.config;

import com.aicomic.entity.PromptTemplate;
import com.aicomic.repository.PromptTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 提示词模板初始化器
 * 应用启动时自动从 JSON 文件加载默认模板到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptTemplateInitializer implements CommandLineRunner {

    private final PromptTemplateRepository promptTemplateRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化提示词模板...");
        
        // 检查是否已有模板
        long existingCount = promptTemplateRepository.count();
        if (existingCount > 0) {
            log.info("已存在 {} 个提示词模板，跳过初始化", existingCount);
            return;
        }
        
        // 加载 JSON 文件
        ClassPathResource resource = new ClassPathResource("data/prompt-templates.json");
        if (!resource.exists()) {
            log.error("提示词模板文件不存在：data/prompt-templates.json");
            return;
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            List<PromptTemplate> templates = objectMapper.readValue(
                inputStream, 
                new TypeReference<List<PromptTemplate>>() {}
            );
            
            // 批量保存
            List<PromptTemplate> saved = promptTemplateRepository.saveAll(templates);
            log.info("成功初始化 {} 个提示词模板", saved.size());
            
            // 打印模板分类统计
            templates.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    PromptTemplate::getCategory, 
                    java.util.stream.Collectors.counting()))
                .forEach((category, count) -> 
                    log.info("  - {}: {} 个模板", category, count)
                );
            
        } catch (Exception e) {
            log.error("加载提示词模板失败", e);
        }
    }
}
