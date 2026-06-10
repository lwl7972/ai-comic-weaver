package com.aicomic.entity;

import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.21 音频轨道表 - 音频处理模块
 */
@Entity
@Table(name = "audio_track")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AudioTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    /** 轨道名称 (如: BGM、配音、音效) */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AudioTrackType type;

    /** 关联的素材ID */
    @Column(name = "asset_id")
    private Long assetId;

    /** 文件路径 */
    @Column(name = "file_path")
    private String filePath;

    /** 时长 (秒) */
    private Double duration;

    /** 开始时间偏移 (秒) */
    @Column(name = "start_offset")
    private Double startOffset = 0.0;

    /** 音量 0.0~1.0 */
    private Double volume = 1.0;

    /** 是否循环 */
    @Column(name = "is_looping")
    private Boolean isLooping = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum AudioTrackType { BGM, VOICEOVER, EFFECT, TTS }
}
