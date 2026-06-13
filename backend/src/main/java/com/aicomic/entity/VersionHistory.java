package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.18 版本历史表 - 支持撤销/重做和版本回滚
 *
 * 跟踪对象类型: SCRIPT / EPISODE / CHARACTER / SCENE / STORYBOARD
 */
@Entity
@Table(name = "version_history", indexes = {@Index(name = "idx_version_history_object", columnList = "object_id, object_type")})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VersionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** 对象类型 */
    @Column(name = "object_type", nullable = false, length = 20)
    private String objectType;       // SCRIPT | EPISODE | CHARACTER | SCENE | STORYBOARD

    /** 对象ID */
    @Column(name = "object_id", nullable = false)
    private Long objectId;

    /** 版本号 */
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** 快照数据 (JSON) */
    @Lob
    @Column(name = "snapshot_data", columnDefinition = "TEXT", nullable = false)
    private String snapshotData;

    /** 变更备注 */
    @Column(name = "change_note", length = 500)
    private String changeNote;

    /** 操作来源 */
    @Column(name = "created_by", length = 100)
    private String createdBy = "user";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
