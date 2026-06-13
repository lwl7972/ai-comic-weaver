package com.aicomic.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频生成响应 DTO
 */
@Data
public class VideoGenerationResponse {

    /** 任务 ID */
    private String taskId;

    /** 任务类型 */
    private String taskType;

    /** 视频 URL */
    private String videoUrl;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED */
    private String status;

    /** 优先级 */
    private String priority;

    /** 进度 (0-100) */
    private Integer progress;

    /** 错误信息 */
    private String errorMessage;

    /** 提交时间 */
    private LocalDateTime submittedAt;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 预估剩余时间（秒） */
    private Integer estimatedTimeRemaining;

    /** 重试次数 */
    private Integer retryCount;
}
