package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.4 剧本表
 */
@Entity
@Table(name = "script", indexes = {@Index(name = "idx_script_project_id", columnList = "project_id")})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String title;

    /** 大纲内容 (A.5 提示词输出) */
    @Column(columnDefinition = "TEXT")
    private String outline;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step")
    private ScriptStep currentStep = ScriptStep.OUTLINE;

    @Column(name = "total_episodes")
    private Integer totalEpisodes;

    @Enumerated(EnumType.STRING)
    private ScriptStatus status = ScriptStatus.DRAFT;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum ScriptStep {
        OUTLINE, EPISODES, DRAFT, REFINED
    }

    public enum ScriptStatus {
        DRAFT, IN_PROGRESS, COMPLETED, ERROR
    }
}
