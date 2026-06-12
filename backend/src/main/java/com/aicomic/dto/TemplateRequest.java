package com.aicomic.dto;

import com.aicomic.entity.ProjectTemplate;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 模板创建/更新请求 DTO
 */
@Data
public class TemplateRequest {

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过100")
    private String name;

    @Size(max = 500, message = "模板描述长度不能超过500")
    private String description;

    /** 风格类型 */
    private ProjectTemplate.StyleType style;

    /** 模板数据JSON */
    private String templateData;
}
