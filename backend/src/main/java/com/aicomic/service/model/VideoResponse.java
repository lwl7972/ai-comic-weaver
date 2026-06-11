package com.aicomic.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频模型响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponse {
    private boolean success;
    /** 异步任务ID（用于轮询） */
    private String taskId;
    /** 视频URL（轮询完成后返回） */
    private String videoUrl;
    /** 任务状态: SUBMITTED / PROCESSING / COMPLETED / FAILED */
    private String status;
    private String errorMessage;
}
