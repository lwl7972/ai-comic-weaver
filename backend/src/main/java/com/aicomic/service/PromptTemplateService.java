package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.PromptTemplate;
import com.aicomic.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提示词模板引擎服务
 * 管理提示词模板的 CRUD 及变量替换渲染
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;

    /** 占位符正则: {variableName} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

    /**
     * 获取所有模板
     */
    public List<PromptTemplate> getAllTemplates() {
        return promptTemplateRepository.findAll();
    }

    /**
     * 按分类获取模板
     */
    public List<PromptTemplate> getTemplatesByCategory(PromptTemplate.TemplateCategory category) {
        return promptTemplateRepository.findByCategory(category);
    }

    /**
     * 获取默认模板
     */
    public List<PromptTemplate> getDefaultTemplates() {
        return promptTemplateRepository.findByIsDefaultTrue();
    }

    /**
     * 获取单个模板
     */
    public Optional<PromptTemplate> getTemplate(Long id) {
        return promptTemplateRepository.findById(id);
    }

    /**
     * 创建模板
     */
    @Transactional
    public PromptTemplate createTemplate(PromptTemplate template) {
        return promptTemplateRepository.save(template);
    }

    /**
     * 更新模板
     */
    @Transactional
    public PromptTemplate updateTemplate(Long id, PromptTemplate updated) {
        return promptTemplateRepository.findById(id).map(template -> {
            if (updated.getName() != null) template.setName(updated.getName());
            if (updated.getCategory() != null) template.setCategory(updated.getCategory());
            if (updated.getContent() != null) template.setContent(updated.getContent());
            if (updated.getVariables() != null) template.setVariables(updated.getVariables());
            if (updated.getIsDefault() != null) template.setIsDefault(updated.getIsDefault());
            // 版本号递增
            template.setVersion(template.getVersion() + 1);
            return promptTemplateRepository.save(template);
        }).orElseThrow(() -> new ResourceNotFoundException("提示词模板", id));
    }

    /**
     * 删除模板
     */
    @Transactional
    public void deleteTemplate(Long id) {
        if (!promptTemplateRepository.existsById(id)) {
            throw new ResourceNotFoundException("提示词模板", id);
        }
        promptTemplateRepository.deleteById(id);
    }

    /**
     * 渲染提示词：替换模板中的变量占位符
     *
     * @param templateId 模板 ID
     * @param variables  变量键值对
     * @return 渲染后的提示词文本
     */
    public String renderTemplate(Long templateId, Map<String, String> variables) {
        PromptTemplate template = promptTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("提示词模板", templateId));

        return renderContent(template.getContent(), variables);
    }

    /**
     * 渲染提示词内容：替换变量占位符
     *
     * @param content   模板内容（含 {variableName} 占位符）
     * @param variables 变量键值对
     * @return 渲染后的文本
     */
    public String renderContent(String content, Map<String, String> variables) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        Map<String, String> safeVariables = variables != null ? variables : new HashMap<>();

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = safeVariables.getOrDefault(varName, "{" + varName + "}");
            // 转义替换文本中的特殊正则字符
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 校验模板变量完整性
     *
     * @param content   模板内容
     * @param variables 提供的变量
     * @return 缺失的变量名列表
     */
    public List<String> validateVariables(String content, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        java.util.Set<String> requiredVars = new java.util.HashSet<>();
        while (matcher.find()) {
            requiredVars.add(matcher.group(1));
        }

        return requiredVars.stream()
                .filter(var -> !variables.containsKey(var))
                .collect(Collectors.toList());
    }
}
