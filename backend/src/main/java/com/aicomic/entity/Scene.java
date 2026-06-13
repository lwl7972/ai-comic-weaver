package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.8 场景表 - 支持四视图生成 (ADR-11)
 */
@Entity
@Table(name = "scene", indexes = {@Index(name = "idx_scene_project_id", columnList = "project_id")})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_of_day")
    private TimeOfDay timeOfDay;

    @Enumerated(EnumType.STRING)
    private Weather weather;

    /** 场景风格关键词 */
    @Column(name = "style_hint")
    private String styleHint;

    // 四视图 URLs (ADR-11)
    @Column(name = "front_view_url")
    private String frontViewUrl;

    @Column(name = "back_view_url")
    private String backViewUrl;

    @Column(name = "left_view_url")
    private String leftViewUrl;

    @Column(name = "right_view_url")
    private String rightViewUrl;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum TimeOfDay {
        MORNING, NOON, AFTERNOON, EVENING, NIGHT, DAWN
    }

    public enum Weather {
        SUNNY, CLOUDY, RAINY, SNOWY, FOGGY
    }
}
