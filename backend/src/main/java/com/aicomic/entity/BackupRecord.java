package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.20 备份记录表
 */
@Entity
@Table(name = "backup_record")
@Data
public class BackupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    /** 备份文件路径 */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /** 备份大小 (bytes) */
    @Column(name = "file_size")
    private Long fileSize;

    /** 备份说明 */
    @Lob
    private String description;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
