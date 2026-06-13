package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.12 扣子工作流配置表 - ADR-5 结构化字段设计
 *
 * 字段结构化替代JSON存储:
 *   workflow_id / input_mapping / output_field / bot_id / app_id
 */
@Entity
@Table(name = "coze_workflow_config")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CozeWorkflowConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 扣子工作流 ID */
    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    /** 输入字段映射 (JSON) */
    @Lob
    @Column(name = "input_mapping", columnDefinition = "TEXT")
    private String inputMapping;

    /** 输出字段名 */
    @Column(name = "output_field", length = 100)
    private String outputField;

    /** 扣子 Bot ID */
    @Column(name = "bot_id", length = 100)
    private String botId;

    /** 扣子 App ID */
    @Column(name = "app_id", length = 100)
    private String appId;

    /** 用途分类 */
    @Enumerated(EnumType.STRING)
    private CozePurpose purpose;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum CozePurpose {
        SCRIPT_TO_STORYBOARD,
        CHARACTER_MAKEUP,
        SCENE_VIEW_GENERATION,
        OTHER
    }
}
