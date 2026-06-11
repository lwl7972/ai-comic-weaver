package com.aicomic.service.model;

import com.aicomic.entity.ModelConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 扣子工作流请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CozeWorkflowRequest {
    private String workflowId;
    /** 工作流输入参数（JSON字符串） */
    private String parameters;
    private ModelConfig modelConfig;
    private String botId;
    private String appId;
}
