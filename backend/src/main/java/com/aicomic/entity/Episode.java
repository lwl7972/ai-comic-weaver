package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.5 剧集表
 */
@Entity
@Table(name = "episode", indexes = {@Index(name = "idx_episode_script_id", columnList = "script_id")})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Column(nullable = false)
    private String title;

    /** 剧本正文内容 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String scriptContent;

    /** 结构化解析数据JSON (A.8.1 三步流程步骤1的输出) */
    @Column(name = "parsed_data", columnDefinition = "TEXT")
    private String parsedData;

    @Enumerated(EnumType.STRING)
    private EpisodeStatus status = EpisodeStatus.DRAFT;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum EpisodeStatus {
        DRAFT, PARSED, READY, ERROR
    }
}
