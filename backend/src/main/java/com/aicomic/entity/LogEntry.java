package com.aicomic.entity;

import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.25 日志条目表 - 错误处理和日志模块
 */
@Entity
@Table(name = "log_entry")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** 日志级别 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogLevel level = LogLevel.INFO;

    /** 模块来源 */
    @Column(name = "source_module", length = 50)
    private String sourceModule;      // SCRIPT / CHARACTER / SCENE / ...

    /** 日志消息 */
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    /** 异常堆栈 (如有) */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /** 关联的项目ID (可选) */
    @Column(name = "project_id")
    private Long projectId;

    /** 关联任务ID (可选) */
    @Column(name = "task_id")
    private Long taskId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL
    }
}
