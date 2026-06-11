package com.aicomic.service.model;

import com.aicomic.entity.ModelConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图像模型请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageRequest {
    private String prompt;
    private ModelConfig modelConfig;
    /** 参考图URL（可选） */
    private String referenceImageUrl;
    /** 图片宽度 */
    private Integer width;
    /** 图片高度 */
    private Integer height;
}
