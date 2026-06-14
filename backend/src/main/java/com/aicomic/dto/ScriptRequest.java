package com.aicomic.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 剧本创建/更新请求 DTO
 * 隔离 Entity 与 API 契约，防止客户端设置 id/createdAt 等字段
 */
@Data
public class ScriptRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    private String outline;

    private String currentStep;

    private Integer totalEpisodes;

    private String status;
}
