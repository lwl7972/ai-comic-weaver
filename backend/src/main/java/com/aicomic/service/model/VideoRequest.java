package com.aicomic.service.model;

import com.aicomic.entity.ModelConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频模型请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoRequest {
    private String prompt;
    private ModelConfig modelConfig;
    /** 首帧图URL（ADR-16: 分镜生成图作为首帧） */
    private String imageUrl;
    /** 参考音频URL（可选） */
    private String audioUrl;
    /** 视频时长（秒） */
    private Integer duration;
}
