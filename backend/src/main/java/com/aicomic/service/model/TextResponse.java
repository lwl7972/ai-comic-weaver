package com.aicomic.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本模型响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextResponse {
    private boolean success;
    private String text;
    private String errorMessage;
    /** 消耗的 token 数 */
    private Integer tokensUsed;
}
