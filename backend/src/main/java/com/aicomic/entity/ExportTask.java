package com.aicomic.entity;

import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.23 导出任务表 - S级输出模块
 */
@Entity
@Table(name = "export_task")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExportFormat format;

    /** 输出质量预设 */
    @Enumerated(EnumType.STRING)
    private ExportQuality quality;

    /** 分辨率 (如 "1920x1080") */
    @Column(name = "resolution")
    private String resolution;

    /** 目标文件路径 */
    @Column(name = "output_path")
    private String outputPath;

    /** 文件大小 */
    @Column(name = "file_size")
    private Long fileSize;

    /** 任务状态 */
    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.PENDING;

    /** 进度 0-100 */
    private Integer progress = 0;

    /** 错误信息 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum ExportFormat { MP4, MOV, AVI, WEBM, GIF }
    public enum ExportQuality { LOW, MEDIUM, HIGH, ULTRA }

    // Re-use GenerationTask.TaskStatus for consistency
    public enum TaskStatus { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED }
}
