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
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * 提示词模板初始化器
 * 首次运行：从 classpath JSON 初始化 → 保存到本地文件
 * 后续运行：从本地文件加载（如存在），否则从数据库读取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptTemplateInitializer implements CommandLineRunner {

    private final PromptTemplateRepository promptTemplateRepository;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;

    /** 本地模板文件路径：data/prompt-templates.json */
    public static final String LOCAL_TEMPLATES_FILE = "prompt-templates.json";

    @Override
    public void run(String... args) throws Exception {
        log.info("开始初始化提示词模板...");

        // 检查数据库是否已有模板
        long existingCount = promptTemplateRepository.count();
        if (existingCount > 0) {
            log.info("已存在 {} 个提示词模板，跳过初始化", existingCount);
            migrateIfNeeded();
            // 确保本地文件存在（从数据库导出）
            ensureLocalFile();
            return;
        }

        // 数据库为空：尝试从本地文件加载
        File localFile = getLocalFile();
        if (localFile.exists() && localFile.length() > 0) {
            loadFromLocalFile(localFile);
            return;
        }

        // 本地文件不存在：从 classpath 初始化
        loadFromClasspath();
    }

    /**
     * 从 classpath JSON 文件加载默认模板，同时保存到本地文件
     */
    private void loadFromClasspath() {
        ClassPathResource resource = new ClassPathResource("data/prompt-templates.json");
        if (!resource.exists()) {
            log.error("提示词模板文件不存在：data/prompt-templates.json");
            return;
        }

        try (InputStream inputStream = resource.getInputStream()) {
            List<PromptTemplate> templates = objectMapper.readValue(
                inputStream, new TypeReference<List<PromptTemplate>>() {});
            List<PromptTemplate> saved = promptTemplateRepository.saveAll(templates);
            log.info("从 classpath 初始化 {} 个提示词模板", saved.size());

            // 保存到本地文件
            saveToLocalFile(saved);
        } catch (Exception e) {
            log.error("加载提示词模板失败", e);
        }
    }

    /**
     * 从本地 JSON 文件加载模板
     */
    private void loadFromLocalFile(File localFile) {
        try {
            List<PromptTemplate> templates = objectMapper.readValue(
                localFile, new TypeReference<List<PromptTemplate>>() {});
            List<PromptTemplate> saved = promptTemplateRepository.saveAll(templates);
            log.info("从本地文件加载 {} 个提示词模板: {}", saved.size(), localFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("从本地文件加载模板失败: {}", e.getMessage());
        }
    }

    /**
     * 确保本地文件存在（数据库有但本地文件丢失时重建）
     */
    private void ensureLocalFile() {
        File localFile = getLocalFile();
        if (!localFile.exists() || localFile.length() == 0) {
            List<PromptTemplate> all = promptTemplateRepository.findAll();
            if (!all.isEmpty()) {
                saveToLocalFile(all);
                log.info("重建本地模板文件: {}", localFile.getAbsolutePath());
            }
        }
    }

    /**
     * 保存模板到本地 JSON 文件
     */
    public void saveToLocalFile(List<PromptTemplate> templates) {
        try {
            File localFile = getLocalFile();
            localFile.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(localFile, templates);
            log.info("模板已保存到本地文件: {}", localFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("保存模板到本地文件失败: {}", e.getMessage());
        }
    }

    /**
     * 获取本地模板文件路径
     */
    private File getLocalFile() {
        String dataDir = System.getProperty("app.data.dir", "./data");
        return new File(dataDir, LOCAL_TEMPLATES_FILE);
    }

    /**
     * 按需迁移：仅在检测到旧格式时执行
     */
    private void migrateIfNeeded() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT created_at FROM prompt_template WHERE created_at IS NOT NULL LIMIT 1");
            if (rs.next()) {
                String createdAt = rs.getString("created_at");
                rs.close();
                if (createdAt != null && !createdAt.matches("\\d{13}")) {
                    return;
                }
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
