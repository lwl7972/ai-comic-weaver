package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.10 生成任务表 - 统一管理所有AI生成任务的调度和状态
 *
 * 任务调度方案 (ADR-8): Spring @Async + ThreadPoolTaskExecutor
 */
@Entity
@Table(name = "generation_task", indexes = {@Index(name = "idx_generation_task_target", columnList = "target_type, target_id")})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GenerationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** 生成用途 (ADR-7) */
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_purpose", nullable = false)
    private Storyboard.GenerationPurpose generationPurpose;

    /** 关联的目标ID (character_id / scene_id / storyboard_id) */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 目标类型 */
    @Column(name = "target_type", length = 20)
    private String targetType;       // CHARACTER / SCENE / STORYBOARD

    /** 模型配置ID → model_config.id */
    @Column(name = "model_provider_id")
    private Long modelProviderId;

    /** 任务状态 */
    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;

    /** 进度百分比 0-100 */
    private Integer progress = 0;

    /** 生成的图片URL */
    @Column(name = "result_image_url")
    private String resultImageUrl;

    /** 生成的视频URL */
    @Column(name = "result_video_url")
    private String resultVideoUrl;

    /** 错误信息 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum TaskStatus {
        PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
    }
}
