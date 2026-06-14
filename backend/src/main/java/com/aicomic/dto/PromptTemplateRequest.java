package com.aicomic.dto;

import com.aicomic.entity.PromptTemplate;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提示词模板创建/更新请求 DTO - 防止客户端篡改 id/version/createdAt 等字段
 */
@Data
public class PromptTemplateRequest {

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过100")
    private String name;

    private PromptTemplate.TemplateCategory category;

    @NotBlank(message = "模板内容不能为空")
    private String content;

    private String variables;

    private Boolean isDefault = false;
}
