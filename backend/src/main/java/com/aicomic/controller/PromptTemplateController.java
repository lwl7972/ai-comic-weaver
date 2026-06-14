package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.PromptTemplate;
import com.aicomic.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板控制器
 * 提供模板 CRUD、渲染、校验等 REST API
 */
@RestController
@RequestMapping("/api/v1/prompt-templates")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService templateService;

    /**
     * 获取所有模板
     */
    @GetMapping
    public ApiResponse<List<PromptTemplate>> getAllTemplates() {
        return ApiResponse.success(templateService.getAllTemplates());
    }

    /**
     * 按分类获取模板
     */
    @GetMapping("/category/{category}")
    public ApiResponse<List<PromptTemplate>> getTemplatesByCategory(
            @PathVariable PromptTemplate.TemplateCategory category
    ) {
        return ApiResponse.success(templateService.getTemplatesByCategory(category));
    }

    /**
     * 获取默认模板
     */
    @GetMapping("/defaults")
    public ApiResponse<List<PromptTemplate>> getDefaultTemplates() {
        return ApiResponse.success(templateService.getDefaultTemplates());
    }

    /**
     * 获取单个模板
     */
    @GetMapping("/{id}")
    public ApiResponse<PromptTemplate> getTemplate(@PathVariable Long id) {
        return templateService.getTemplate(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(ApiResponse.NOT_FOUND, "模板不存在"));
    }

    /**
     * 创建模板
     */
    @PostMapping
    public ApiResponse<PromptTemplate> createTemplate(@RequestBody PromptTemplate template) {
        return ApiResponse.success(templateService.createTemplate(template));
    }

    /**
     * 更新模板
     */
    @PutMapping("/{id}")
    public ApiResponse<PromptTemplate> updateTemplate(
            @PathVariable Long id,
            @RequestBody PromptTemplate template
    ) {
        try {
            return ApiResponse.success(templateService.updateTemplate(id, template));
        } catch (Exception e) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "模板不存在");
        }
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ApiResponse.success();
    }

    /**
     * 渲染模板
     */
    @PostMapping("/{id}/render")
    public ApiResponse<String> renderTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, String> variables
    ) {
        try {
            String rendered = templateService.renderTemplate(id, variables);
            return ApiResponse.success(rendered);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ApiResponse.PARAM_ERROR, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "模板不存在");
        }
    }

    /**
     * 校验模板变量完整性
     */
    @GetMapping("/{id}/validate")
    public ApiResponse<Map<String, Object>> validateTemplate(@PathVariable Long id) {
        Map<String, Object> result = templateService.validateTemplate(id);
        Boolean isValid = (Boolean) result.get("valid");
        return Boolean.TRUE.equals(isValid) ? ApiResponse.success(result) : ApiResponse.error(ApiResponse.PARAM_ERROR, "模板变量校验失败");
    }
}
