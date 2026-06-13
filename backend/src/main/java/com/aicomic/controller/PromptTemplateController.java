package com.aicomic.controller;

import com.aicomic.entity.PromptTemplate;
import com.aicomic.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<PromptTemplate>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    /**
     * 按分类获取模板
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<PromptTemplate>> getTemplatesByCategory(
            @PathVariable PromptTemplate.TemplateCategory category
    ) {
        return ResponseEntity.ok(templateService.getTemplatesByCategory(category));
    }

    /**
     * 获取默认模板
     */
    @GetMapping("/defaults")
    public ResponseEntity<List<PromptTemplate>> getDefaultTemplates() {
        return ResponseEntity.ok(templateService.getDefaultTemplates());
    }

    /**
     * 获取单个模板
     */
    @GetMapping("/{id}")
    public ResponseEntity<PromptTemplate> getTemplate(@PathVariable Long id) {
        return templateService.getTemplate(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建模板
     */
    @PostMapping
    public ResponseEntity<PromptTemplate> createTemplate(@RequestBody PromptTemplate template) {
        return ResponseEntity.ok(templateService.createTemplate(template));
    }

    /**
     * 更新模板
     */
    @PutMapping("/{id}")
    public ResponseEntity<PromptTemplate> updateTemplate(
            @PathVariable Long id,
            @RequestBody PromptTemplate template
    ) {
        try {
            return ResponseEntity.ok(templateService.updateTemplate(id, template));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 渲染模板
     */
    @PostMapping("/{id}/render")
    public ResponseEntity<String> renderTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, String> variables
    ) {
        try {
            String rendered = templateService.renderTemplate(id, variables);
            return ResponseEntity.ok(rendered);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 校验模板变量完整性
     */
    @GetMapping("/{id}/validate")
    public ResponseEntity<Map<String, Object>> validateTemplate(@PathVariable Long id) {
        Map<String, Object> result = templateService.validateTemplate(id);
        Boolean isValid = (Boolean) result.get("valid");
        return isValid ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
