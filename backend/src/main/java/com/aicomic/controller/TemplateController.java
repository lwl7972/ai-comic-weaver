package com.aicomic.controller;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.TemplateRequest;
import com.aicomic.entity.Project;
import com.aicomic.entity.ProjectTemplate;
import com.aicomic.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目模板 REST API - 模板CRUD与从模板创建项目
 */
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /** GET /api/v1/templates?type=xxx - 模板列表 */
    @GetMapping
    public ApiResponse<List<ProjectTemplate>> list(@RequestParam(required = false) String type) {
        return ApiResponse.success(templateService.getTemplates(type));
    }

    /** GET /api/v1/templates/{id} - 模板详情 */
    @GetMapping("/{id}")
    public ApiResponse<ProjectTemplate> detail(@PathVariable Long id) {
        try {
            return ApiResponse.success(templateService.getTemplate(id));
        } catch (ResourceNotFoundException e) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "模板不存在");
        }
    }

    /** POST /api/v1/templates - 创建模板 */
    @PostMapping
    public ApiResponse<ProjectTemplate> create(@RequestBody TemplateRequest req) {
        ProjectTemplate template = new ProjectTemplate();
        template.setName(req.getName());
        template.setDescription(req.getDescription());
        template.setStyle(req.getStyle());
        template.setTemplateData(req.getTemplateData());
        return ApiResponse.success(templateService.saveTemplate(template));
    }

    /** PUT /api/v1/templates/{id} - 更新模板 */
    @PutMapping("/{id}")
    public ApiResponse<ProjectTemplate> update(@PathVariable Long id, @RequestBody TemplateRequest req) {
        try {
            ProjectTemplate template = templateService.getTemplate(id);
            if (req.getName() != null) template.setName(req.getName());
            if (req.getDescription() != null) template.setDescription(req.getDescription());
            if (req.getStyle() != null) template.setStyle(req.getStyle());
            if (req.getTemplateData() != null) template.setTemplateData(req.getTemplateData());
            return ApiResponse.success(templateService.saveTemplate(template));
        } catch (ResourceNotFoundException e) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "模板不存在");
        }
    }

    /** DELETE /api/v1/templates/{id} - 删除模板 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            templateService.deleteTemplate(id);
            return ApiResponse.success();
        } catch (ResourceNotFoundException e) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "模板不存在");
        } catch (IllegalStateException e) {
            return ApiResponse.error(ApiResponse.FORBIDDEN, e.getMessage());
        }
    }

    /** POST /api/v1/templates/{id}/create-project - 从模板创建项目 */
    @PostMapping("/{id}/create-project")
    public ApiResponse<Project> createProject(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.get("description");
        if (name == null || name.isBlank()) {
            return ApiResponse.error(ApiResponse.PARAM_ERROR, "项目名称不能为空");
        }
        try {
            return ApiResponse.success(templateService.createProjectFromTemplate(id, name, description));
        } catch (ResourceNotFoundException e) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "模板不存在");
        }
    }
}
