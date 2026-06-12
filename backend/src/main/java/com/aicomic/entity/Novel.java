package com.aicomic.entity;

import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.2 小说表
 */
@Entity
@Table(name = "novel", indexes = {@Index(name = "idx_novel_project_id", columnList = "project_id")})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Novel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String title;

    private String author;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "total_chapters")
    private Integer totalChapters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NovelStatus status = NovelStatus.IMPORTING;

    private String errorMessage;

    @Column(name = "imported_at")
    @CreationTimestamp
    private LocalDateTime importedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ============================================================
    // Enums
    // ============================================================

    public enum NovelStatus {
        IMPORTING, SUMMARIZING, CONVERTING, COMPLETED, ERROR
    }
}
