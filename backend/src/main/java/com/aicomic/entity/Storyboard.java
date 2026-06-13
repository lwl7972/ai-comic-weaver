package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.9 分镜表 - ADR-19 三步流程(解析→编辑→生成)的核心数据实体
 * 支持专业电影级参数: 景别/机位/运镜/情绪
 */
@Entity
@Table(name = "storyboard", indexes = {@Index(name = "idx_storyboard_episode_id", columnList = "episode_id")}, uniqueConstraints = {@UniqueConstraint(name = "uk_storyboard_episode_seq", columnNames = {"episode_id", "sequence"})})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Storyboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    /** 分镜序号（从0连续编号） */
    @Column(nullable = false)
    private Integer sequence;

    /** 时间范围预估，如 "0-4s" */
    @Column(name = "time_range", length = 20)
    private String timeRange;

    /** 承接上镜描述 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String continuity;

    /** 角色对话，格式: [角色名, 情绪]:"台词" */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String dialogue;

    /** 动作描述 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String action;

    /** 情绪/氛围标签 */
    @Column(length = 50)
    private String emotion;

    // --- 专业电影级参数 ---

    /** 景别 (Shot Size) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ShotSize shotSize = ShotSize.MEDIUM;

    /** 机位角度 */
    @Enumerated(EnumType.STRING)
    @Column(name = "camera_angle", nullable = false)
    private CameraAngle cameraAngle = CameraAngle.EYE_LEVEL;

    /** 运镜方式 */
    @Enumerated(EnumType.STRING)
    @Column(name = "camera_movement", nullable = false)
    private CameraMovement cameraMovement = CameraMovement.STATIC;

    /** 本分镜涉及的角色名列表 (JSON数组) */
    @Column(name = "involved_characters", columnDefinition = "TEXT")
    private String involvedCharacters;

    /** 本分镜涉及的角色ID列表 (JSON数组，角色定妆图引用传递) */
    @Column(name = "involved_character_ids", columnDefinition = "TEXT")
    private String involvedCharacterIds;

    /** 本分镜所在场景名称 */
    @Column(name = "involved_scene_name", length = 200)
    private String involvedSceneName;

    /** 本分镜所在场景ID (场景图引用传递) */
    @Column(name = "involved_scene_id")
    private Long involvedSceneId;

    /** 收集的参考图URL列表 (JSON数组: 角色定妆图+场景图) */
    @Column(name = "reference_image_urls", columnDefinition = "TEXT")
    private String referenceImageUrls;

    /** 背景音效建议 */
    @Column(name = "bg_sound", length = 200)
    private String bgSound;

    // --- AI 生成产物 ---

    /** 生成的分镜图片URL */
    @Column(name = "generated_image_url")
    private String generatedImageUrl;

    /** 生成的视频URL (导演模块) */
    @Column(name = "generated_video_url")
    private String generatedVideoUrl;

    /** 生成用途区分 (ADR-7) */
    @Enumerated(EnumType.STRING)
    @Column(name = "generation_purpose")
    private GenerationPurpose generationPurpose;

    /** 当前状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoryboardStatus status = StoryboardStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // ============================================================
    // Enums - 电影级参数枚举
    // ============================================================

    public enum ShotSize {
        EXTREME_CLOSE_UP, CLOSE_UP, MEDIUM_CLOSE_UP,
        MEDIUM, MEDIUM_WIDE, WIDE, EXTREME_WIDE
    }

    public enum CameraAngle {
        EYE_LEVEL, HIGH_ANGLE, LOW_ANGLE, BIRD_EYE, DUTCH_ANGLE
    }

    public enum CameraMovement {
        STATIC, PAN_LEFT, PAN_RIGHT, TILT_UP, TILT_DOWN,
        ZOOM_IN, ZOOM_OUT, TRACKING, CRANE, HANDHELD
    }

    public enum GenerationPurpose {
        CHARACTER_MAKEUP, SCENE_VIEW, SCENE_QUAD_VIEW,
        STORYBOARD_IMAGE, STORYBOARD_VIDEO
    }

    public enum StoryboardStatus {
        PENDING, IMAGE_GENERATING, IMAGE_DONE,
        VIDEO_GENERATING, VIDEO_DONE, ERROR
    }
}
