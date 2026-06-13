package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.27 通知表 - 用户通知和系统提醒
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** 通知类型 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /** 标题 */
    @Column(nullable = false)
    private String title;

    /** 内容 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 是否已读 */
    @Column(name = "is_read")
    private Boolean isRead = false;

    /** 关联项目ID */
    @Column(name = "project_id")
    private Long projectId;

    /** 关联任务ID */
    @Column(name = "task_id")
    private Long taskId;

    /** 动作链接 (可选，如打开某个页面) */
    @Column(name = "action_link", length = 500)
    private String actionLink;

    /** 过期时间 */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum NotificationType {
        TASK_COMPLETE,       // 任务完成
        TASK_FAILED,         // 任务失败
        PIPELINE_ADVANCED,   // 流水线阶段推进
        DIRTY_WARNING,       // 脏标记警告 (ADR-20)
        BACKUP_REMINDER,     // 备份提醒
        UPDATE_AVAILABLE,    // 更新可用 (8.1.5)
        SYSTEM              // 系统通知
    }
}
