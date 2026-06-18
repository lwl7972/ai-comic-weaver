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

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化提示词模板...");

        // 检查是否已有模板（本地已有则跳过，不再每次启动都检查迁移）
        long existingCount = promptTemplateRepository.count();
        if (existingCount > 0) {
            log.info("已存在 {} 个提示词模板，跳过初始化", existingCount);
            // 仅在首次且需要时执行迁移（通过检查时间戳格式判断是否需要修复）
            migrateIfNeeded();
            return;
        }

        // 首次运行：加载 JSON 文件
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

    /**
     * 按需迁移：仅在检测到旧格式时执行，避免每次启动都检查
     */
    private void migrateIfNeeded() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 快速检查：时间戳是否已经是正确格式
            ResultSet rs = stmt.executeQuery("SELECT created_at FROM prompt_template WHERE created_at IS NOT NULL LIMIT 1");
            if (rs.next()) {
                String createdAt = rs.getString("created_at");
                rs.close();
                // 已经是正确格式，无需迁移
                if (createdAt != null && !createdAt.matches("\\d{13}")) {
                    return;
                }
                // 需要修复时间戳
                log.info("检测到旧格式时间戳，正在修复...");
                stmt.execute("UPDATE prompt_template SET " +
                        "created_at = datetime(CAST(created_at AS INTEGER) / 1000, 'unixepoch', 'localtime'), " +
                        "updated_at = datetime(CAST(updated_at AS INTEGER) / 1000, 'unixepoch', 'localtime') " +
                        "WHERE created_at IS NOT NULL AND length(created_at) = 13");
                log.info("时间戳格式修复完成");
            } else {
                rs.close();
            }
        } catch (Exception e) {
            log.warn("迁移检查跳过: {}", e.getMessage());
        }
    }
}
