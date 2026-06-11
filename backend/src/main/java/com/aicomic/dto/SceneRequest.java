package com.aicomic.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 场景创建/更新请求 DTO
 */
@Data
public class SceneRequest {

    @NotBlank(message = "场景名称不能为空")
    @Size(max = 100, message = "场景名称长度不能超过100")
    private String name;

    private String description;

    private String timeOfDay;

    private String weather;

    @Size(max = 200, message = "风格关键词长度不能超过200")
    private String styleHint;
}
