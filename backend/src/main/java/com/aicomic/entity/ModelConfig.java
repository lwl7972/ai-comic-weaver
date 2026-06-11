package com.aicomic.entity;

import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.11 模型配置表 - 管理所有外部AI模型API的连接参数
 *
 * 支持:
 *   文本: OpenAI / Anthropic / 通义千问 / 文心一言
 *   图片: DALL-E / Midjourney / Stable Diffusion
 *   视频: Runway / Pika
 *   音频: OpenAI TTS / 通义语音 / 火山引擎
 */
@Entity
@Table(name = "model_config")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelType type;

    /** API URL */
    @Column(name = "api_url", nullable = false)
    private String apiUrl;

    /** API Key (加密存储) */
    @Column(name = "api_key", nullable = false)
    @ToString.Exclude
    private String apiKey;

    /** 模型名称 (如 gpt-4o, claude-3.5-sonnet) */
    @Column(name = "model_name", nullable = false)
    private String modelName;

    /** 最大token数 (仅文本模型) */
    @Column(name = "max_tokens")
    private Integer maxTokens;

    /** 是否激活 */
    @Column(name = "is_active")
    private Boolean isActive = true;

    /** 优先级 (用于多Key轮询) */
    private Integer priority = 0;

    /** 工作流 ID (Coze/其他工作流平台) */
    @Column(name = "workflow_id")
    private String workflowId;

    /** Bot ID (Coze 专用) */
    @Column(name = "bot_id")
    private String botId;

    /** 应用 ID (Coze 专用) */
    @Column(name = "app_id")
    private String appId;

    /** 是否为 Coze 工作流 */
    @Column(name = "is_coze_workflow")
    private Boolean isCozeWorkflow = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ============================================================
    // Enums
    // ============================================================

    public enum ModelProvider {
        OPENAI, ANTHROPIC, QWEN, ERNIE,          // Text
        DALL_E, MIDJOURNEY, SD,                  // Image
        RUNWAY, PIKA,                             // Video
        TTS_OPENAI, TTS_QWEN, VOLCENGINE,         // Audio
        COZE                                      // Coze Workflow
    }

    public enum ModelType {
        TEXT, IMAGE, VIDEO, AUDIO, WORKFLOW
    }
}
