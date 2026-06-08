package com.aicomic.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.1 项目表
 */
@Entity
@Table(name = "project")
@Data
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StyleType style;

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_stage", nullable = false)
    private PipelineStage pipelineStage = PipelineStage.SCRIPT;

    private Long currentEpisodeId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ============================================================
    // Enums
    // ============================================================

    public enum StyleType { SHORT_DRAMA, COMIC, TRAILER }

    public enum PipelineStage {
        SCRIPT, CHARACTER, SCENE, STORYBOARD, DIRECTOR, OUTPUT
    }
}
