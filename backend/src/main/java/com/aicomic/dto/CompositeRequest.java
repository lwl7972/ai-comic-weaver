package com.aicomic.dto;

import lombok.Data;

/**
 * 视频合成请求 DTO
 */
@Data
public class CompositeRequest {

    /** 剧集ID */
    private Long episodeId;

    /** 是否添加字幕 */
    private boolean addSubtitles = true;

    /** 是否混合音频 */
    private boolean mixAudio = true;

    /** 转场类型: fade / slideleft / slideup / zoom */
    private String transitionType;

    /** 转场时长(秒) */
    private double transitionDuration = 1.0;
}
