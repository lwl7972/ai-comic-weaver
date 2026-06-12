package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.PromptTemplateRequest;
import com.aicomic.entity.PromptTemplate;
import com.aicomic.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板管理 API
 */
@RestController
@RequestMapping("/api/v1/prompt-templates")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    /** GET /api/v1/prompt-templates - 获取所有模板 */
    @GetMapping
    public ApiResponse<List<PromptTemplate>> list() {
        return ApiResponse.success(promptTemplateService.getAllTemplates());
    }

    /** GET /api/v1/prompt-templates?category=SCRIPT - 按分类筛选 */
    @GetMapping(params = "category")
    public ApiResponse<List<PromptTemplate>> byCategory(@RequestParam PromptTemplate.TemplateCategory category) {
        return ApiResponse.success(promptTemplateService.getTemplatesByCategory(category));
    }

    /** GET /api/v1/prompt-templates/defaults - 获取默认模板 */
    @GetMapping("/defaults")
    public ApiResponse<List<PromptTemplate>> defaults() {
        return ApiResponse.success(promptTemplateService.getDefaultTemplates());
    }

    /** GET /api/v1/prompt-templates/{id} - 获取单个模板 */
    @GetMapping("/{id}")
    public ApiResponse<PromptTemplate> detail(@PathVariable Long id) {
        return promptTemplateService.getTemplate(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "提示词模板不存在"));
    }

    /** POST /api/v1/prompt-templates - 创建模板 */
    @PostMapping
    public ApiResponse<PromptTemplate> create(@RequestBody PromptTemplateRequest req) {
        PromptTemplate template = new PromptTemplate();
        applyRequestToTemplate(template, req);
        return ApiResponse.success(promptTemplateService.createTemplate(template));
    }

    /** PUT /api/v1/prompt-templates/{id} - 更新模板（版本号自动递增） */
    @PutMapping("/{id}")
    public ApiResponse<PromptTemplate> update(@PathVariable Long id, @RequestBody PromptTemplateRequest req) {
        PromptTemplate template = promptTemplateService.getTemplate(id)
                .orElseGet(PromptTemplate::new);
        applyRequestToTemplate(template, req);
        return ApiResponse.success(promptTemplateService.updateTemplate(id, template));
    }

    private void applyRequestToTemplate(PromptTemplate template, PromptTemplateRequest req) {
        if (req.getName() != null) template.setName(req.getName());
        if (req.getCategory() != null) template.setCategory(req.getCategory());
        if (req.getContent() != null) template.setContent(req.getContent());
        if (req.getVariables() != null) template.setVariables(req.getVariables());
        if (req.getIsDefault() != null) template.setIsDefault(req.getIsDefault());
    }

    /** DELETE /api/v1/prompt-templates/{id} - 删除模板 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        promptTemplateService.deleteTemplate(id);
        return ApiResponse.success();
    }

    /** POST /api/v1/prompt-templates/{id}/render - 渲染提示词 */
    @PostMapping("/{id}/render")
    public ApiResponse<String> render(@PathVariable Long id, @RequestBody Map<String, String> variables) {
        String rendered = promptTemplateService.renderTemplate(id, variables);
        return ApiResponse.success(rendered);
    }

    /** POST /api/v1/prompt-templates/{id}/validate - 校验模板变量完整性 */
    @PostMapping("/{id}/validate")
    public ApiResponse<Map<String, Object>> validate(@PathVariable Long id) {
        Map<String, Object> result = promptTemplateService.validateTemplate(id);
        return ApiResponse.success(result);
    }
}
