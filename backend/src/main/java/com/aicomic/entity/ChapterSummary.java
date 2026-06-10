package com.aicomic.entity;

import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.3 章节摘要表 - 支撑 ADR-9 分章节摘要策略
 */
@Entity
@Table(name = "chapter_summary")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChapterSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_index", nullable = false)
    private Integer chapterIndex;

    @Column(name = "chapter_title", nullable = false)
    private String chapterTitle;

    /**
     * 结构化摘要JSON (A.8.2 输出格式)
     * 含: summary_text, key_characters, key_locations, plot_turning_points,
     *     cliffhangers, new_introductions, continuity_from_previous
     */
    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SummaryStatus status = SummaryStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum SummaryStatus {
        PENDING, GENERATING, COMPLETED, ERROR
    }
}
