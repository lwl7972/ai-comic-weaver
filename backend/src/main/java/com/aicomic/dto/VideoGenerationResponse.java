package com.aicomic.dto;

import lombok.Data;

/**
 * 视频生成响应 DTO
 */
@Data
public class VideoGenerationResponse {

    /** 任务 ID */
    private Long taskId;

    /** 视频 URL */
    private String videoUrl;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 进度 (0-100) */
    private Integer progress;

    /** 错误信息 */
    private String errorMessage;

    /** 预估剩余时间（秒） */
    private Integer estimatedTimeRemaining;

    /** 实际耗时（秒） */
    private Integer actualDuration;
}
