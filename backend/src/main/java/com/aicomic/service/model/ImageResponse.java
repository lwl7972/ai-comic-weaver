package com.aicomic.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图像模型响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponse {
    private boolean success;
    private String imageUrl;
    private String errorMessage;
}
