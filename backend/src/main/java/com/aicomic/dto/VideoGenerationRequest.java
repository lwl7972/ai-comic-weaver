package com.aicomic.dto;

import lombok.Data;

/**
 * 视频生成请求 DTO
 */
@Data
public class VideoGenerationRequest {

    /** 项目 ID */
    private Long projectId;

    /** 剧集 ID */
    private Long episodeId;

    /** 生成模式：FULL_EPISODE(整集) / SINGLE_SHOT(单镜头) */
    private String generationMode;

    /** 参考图 URL（可选） */
    private String referenceImageUrl;

    /** 提示词（可选，不传则自动构建） */
    private String prompt;

    /** 视频时长（秒） */
    private Integer duration;

    /** 分辨率 */
    private String resolution;

    /** 优先级：HIGH/MEDIUM/LOW */
    private String priority;

    /** 是否启用重试 */
    private Boolean enableRetry = true;

    /** 最大重试次数 */
    private Integer maxRetries = 3;
}
