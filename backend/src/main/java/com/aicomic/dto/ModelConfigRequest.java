package com.aicomic.dto;

import com.aicomic.entity.ModelConfig;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 模型配置创建/更新请求 DTO - 防止客户端篡改 id/apiKey 等敏感字段
 */
@Data
public class ModelConfigRequest {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称长度不能超过100")
    private String name;

    private ModelConfig.ModelProvider provider;

    private ModelConfig.ModelType type;

    @NotBlank(message = "API URL 不能为空")
    private String apiUrl;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private Integer maxTokens;

    private Boolean isActive = true;

    private Integer priority = 0;

    private String workflowId;

    private String botId;

    private String appId;

    private Boolean isCozeWorkflow = false;
}
