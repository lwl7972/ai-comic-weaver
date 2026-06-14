package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.PromptTemplate;
import com.aicomic.repository.PromptTemplateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
    private final ObjectMapper objectMapper;

    /** 占位符正则: {variableName} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

    /**
     * 获取所有模板（带缓存）
     */
    @Cacheable(value = "templates", key = "'all'", unless = "#result.isEmpty()")
    public List<PromptTemplate> getAllTemplates() {
        return promptTemplateRepository.findAll();
    }

    /**
     * 按分类获取模板（带缓存）
     */
    @Cacheable(value = "templates", key = "'category:' + #category", unless = "#result.isEmpty()")
    public List<PromptTemplate> getTemplatesByCategory(PromptTemplate.TemplateCategory category) {
        return promptTemplateRepository.findByCategory(category);
    }

    /**
     * 获取默认模板（带缓存）
     */
    @Cacheable(value = "templates", key = "'default'", unless = "#result.isEmpty()")
    public List<PromptTemplate> getDefaultTemplates() {
        return promptTemplateRepository.findByIsDefaultTrue();
    }

    /**
     * 获取单个模板（带缓存）
     */
    @Cacheable(value = "templates", key = "#id", unless = "#result.empty")
    public Optional<PromptTemplate> getTemplate(Long id) {
        return promptTemplateRepository.findById(id);
    }

    /**
     * 创建模板时清除缓存
     */
    @CacheEvict(value = "templates", allEntries = true)
    @Transactional
    public PromptTemplate createTemplate(PromptTemplate template) {
        return promptTemplateRepository.save(template);
    }

    /**
     * 更新模板时清除缓存
     */
    @CacheEvict(value = "templates", allEntries = true)
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

        // 解析 content 中的所有变量引用
        Set<String> requiredVars = parseContentVariables(template.getContent());

        // 校验必需变量都已提供
        Map<String, String> safeVariables = variables != null ? variables : new HashMap<>();
        List<String> missing = requiredVars.stream()
                .filter(var -> !safeVariables.containsKey(var))
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("缺少必需变量: " + String.join(", ", missing));
        }

        return renderContent(template.getContent(), safeVariables);
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

    /**
     * 校验模板变量定义完整性
     * 对比 content 中的变量引用与 variables JSON 字段中定义的变量名，
     * 找出缺失定义和未使用的定义
     *
     * @param templateId 模板 ID
     * @return 校验结果 Map: { valid, contentVariables, definedVariables, missingDefinitions, unusedDefinitions }
     */
    public Map<String, Object> validateTemplate(Long templateId) {
        PromptTemplate template = promptTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("提示词模板", templateId));

        // 从 content 中解析变量引用
        Set<String> contentVariables = parseContentVariables(template.getContent());

        // 从 variables JSON 字段中解析已定义的变量名
        Set<String> definedVariables = parseDefinedVariables(template.getVariables());

        // content 中有但定义中没有 → missingDefinitions
        List<String> missingDefinitions = contentVariables.stream()
                .filter(var -> !definedVariables.contains(var))
                .sorted()
                .collect(Collectors.toList());

        // 定义中有但 content 中没有 → unusedDefinitions
        List<String> unusedDefinitions = definedVariables.stream()
                .filter(var -> !contentVariables.contains(var))
                .sorted()
                .collect(Collectors.toList());

        boolean valid = missingDefinitions.isEmpty() && unusedDefinitions.isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("contentVariables", new ArrayList<>(contentVariables));
        result.put("definedVariables", new ArrayList<>(definedVariables));
        result.put("missingDefinitions", missingDefinitions);
        result.put("unusedDefinitions", unusedDefinitions);
        return result;
    }

    /**
     * 从模板 content 中解析所有变量引用
     *
     * @param content 模板内容
     * @return 变量名集合
     */
    private Set<String> parseContentVariables(String content) {
        Set<String> variables = new LinkedHashSet<>();
        if (content == null || content.isEmpty()) {
            return variables;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    /**
     * 从 variables JSON 字段中解析已定义的变量名
     * variables 格式为 JSON 数组，如 ["scriptContent","characterList"]
     *
     * @param variablesJson variables JSON 字符串
     * @return 变量名集合
     */
    private Set<String> parseDefinedVariables(String variablesJson) {
        if (variablesJson == null || variablesJson.isEmpty()) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> list = objectMapper.readValue(variablesJson, new TypeReference<List<String>>() {});
            return new LinkedHashSet<>(list);
        } catch (Exception e) {
            log.warn("解析模板变量定义失败: {}", e.getMessage());
            return new LinkedHashSet<>();
        }
    }
}

    /**
     * 批量渲染多个模板
     *
     * @param templateIds 模板 ID 列表
     * @param variables   共享的变量键值对
     * @return Map<模板 ID, 渲染结果>
     */
    public Map<Long, String> batchRenderTemplates(List<Long> templateIds, Map<String, String> variables) {
        return templateIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> renderTemplate(id, variables)
                ));
    }

    /**
     * 根据分类和名称获取模板
     *
     * @param category 分类
     * @param name     模板名称
     * @return 匹配的模板
     */
    public Optional<PromptTemplate> getTemplateByName(PromptTemplate.TemplateCategory category, String name) {
        return getTemplatesByCategory(category).stream()
                .filter(t -> t.getName().equals(name))
                .findFirst();
    }

    /**
     * 渲染分镜生成提示词（特殊处理：支持多角色、多场景）
     *
     * @param templateId      模板 ID
     * @param basicVars       基础变量
     * @param characters      角色列表
     * @param scene           场景描述
     * @param cameraDirection 镜头指令
     * @return 渲染后的提示词
     */
    public String renderStoryboardPrompt(
            Long templateId,
            Map<String, String> basicVars,
            List<String> characters,
            String scene,
            String cameraDirection
    ) {
        Map<String, String> enhancedVars = new HashMap<>(basicVars);
        enhancedVars.put("characters", String.join(", ", characters));
        enhancedVars.put("scene", scene);
        enhancedVars.put("cameraDirection", cameraDirection);
        return renderTemplate(templateId, enhancedVars);
    }
