package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.16 素材资产表 - 统一管理所有文件素材
 *
 * 来源: UPLOAD(用户上传) / GENERATED(AI生成) / EXTRACTED(剧本自动提取)
 * 类型: IMAGE / VIDEO / AUDIO / DOCUMENT
 */
@Entity
@Table(name = "asset")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AssetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType type;

    /** 文件存储路径 (相对 base-dir 的路径) */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /** 文件大小 (bytes) */
    @Column(name = "file_size")
    private Long fileSize;

    /** MIME 类型 */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** 标签列表 (JSON数组) */
    @Lob
    private String tags;

    /** 来源 */
    @Enumerated(EnumType.STRING)
    private AssetSource source;

    /** 关联角色ID (定妆图等) */
    @Column(name = "ref_character_id")
    private Long refCharacterId;

    /** 关联场景ID (场景视图等) */
    @Column(name = "ref_scene_id")
    private Long refSceneId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum AssetType { IMAGE, VIDEO, AUDIO, DOCUMENT }
    public enum AssetSource { UPLOAD, GENERATED, EXTRACTED }
}
