package com.aicomic.dto;

import com.aicomic.entity.Project;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 项目创建/更新请求 DTO - 只暴露业务字段，防止客户端篡改 id/timestamp 等内部字段
 */
@Data
public class ProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称长度不能超过100")
    private String name;

    @Size(max = 500, message = "项目描述长度不能超过500")
    private String description;

    /** 风格类型 */
    private Project.StyleType style;
}
