package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.17 待确认资产表 - ADR-4 AI自动提取 + 用户逐个确认
 *
 * 剧本完成后触发 LLM 提取角色/场景/道具，
 * 用户在此表中逐一确认入库。
 */
@Entity
@Table(name = "extracted_asset", indexes = {@Index(name = "idx_extracted_asset_project_id", columnList = "project_id")})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExtractedAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtractedAssetType type;   // CHARACTER / SCENE / PROP

    @Column(nullable = false)
    private String name;

    /** 描述 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 原文引用（从剧本中提取的原文片段） */
    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    /** 建议的图片生成提示词 */
    @Column(name = "suggested_image_prompt", columnDefinition = "TEXT")
    private String suggestedImagePrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtractedStatus status = ExtractedStatus.PENDING;

    /** 确认后关联的实体ID */
    @Column(name = "confirmed_ref_id")
    private Long confirmedRefId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum ExtractedAssetType { CHARACTER, SCENE, PROP }
    public enum ExtractedStatus { PENDING, CONFIRMED, REJECTED }
}
