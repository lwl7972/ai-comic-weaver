package com.aicomic.service.model;

import com.aicomic.entity.ModelConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本模型请求/响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextRequest {
    private String prompt;
    private ModelConfig modelConfig;
    /** 系统提示词（可选） */
    private String systemPrompt;
    /** 温度参数（可选，默认0.7） */
    private Double temperature;
    /** 最大token数（可选，使用模型配置中的值） */
    private Integer maxTokens;
}
