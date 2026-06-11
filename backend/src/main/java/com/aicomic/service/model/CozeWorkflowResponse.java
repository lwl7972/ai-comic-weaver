package com.aicomic.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 扣子工作流响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CozeWorkflowResponse {
    private boolean success;
    /** 异步运行ID（用于轮询） */
    private String runId;
    /** 工作流输出结果（完成后返回） */
    private String output;
    /** 任务状态: SUBMITTED / PROCESSING / COMPLETED / FAILED */
    private String status;
    private String errorMessage;
    /** debug_url（可选） */
    private String debugUrl;
}
