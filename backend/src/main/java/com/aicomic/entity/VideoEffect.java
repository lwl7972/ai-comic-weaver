package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.22 视频后期效果表 - 视频后期模块 (ADR-17)
 *
 * FFmpeg为主，前端简单预览拼接
 */
@Entity
@Table(name = "video_effect")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VideoEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoEffectType effectType;

    /** 效果参数 (JSON) */
    @Lob
    @Column(name = "effect_params", columnDefinition = "TEXT")
    private String effectParams;

    /** 应用顺序 */
    @Column(name = "order_index")
    private Integer orderIndex;

    /** 是否启用 */
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum VideoEffectType {
        TRANSITION,      // 转场效果
        FILTER,          // 滤镜
        TEXT_OVERLAY,    // 字幕叠加
        WATERMARK,       // 水印 (8.1.4)
        SPEED_ADJUSTMENT,// 变速
        COLOR_GRADE,     // 调色
        CROP,            // 裁剪
        ROTATE           // 旋转
    }
}
