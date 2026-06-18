package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.6 角色表 - 支持6层身份锚点 (ADR-10) 和角色圣经管理
 */
@Entity
@Table(name = "character", indexes = {@Index(name = "idx_character_project_id", columnList = "project_id")})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Character {

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
    private CharacterRole role = CharacterRole.SUPPORTING;

    @Enumerated(EnumType.STRING)
    private Gender gender = Gender.OTHER;

    @Column(name = "age_range")
    private String ageRange;          // e.g., "20-30岁"

    /** 外貌描述（6层锚点中的外观层） */
    @Column(columnDefinition = "TEXT")
    private String appearance;

    /** 性格描述 */
    @Column(columnDefinition = "TEXT")
    private String personality;

    /** 完整6层身份锚点拼装后的提示词 */
    @Column(name = "anchor_prompt", columnDefinition = "TEXT")
    private String anchorPrompt;

    /** 定妆图关联ID → asset.id */
    @Column(name = "reference_image_id")
    private Long referenceImageId;

    /** 是否从剧本自动提取 (ADR-4: AI提取+用户确认) */
    @Column(name = "extracted_from_script")
    private Boolean extractedFromScript = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum CharacterRole {
        PROTAGONIST, ANTAGONIST, SUPPORTING, EXTRA
    }

    public enum Gender {
        MALE, FEMALE, OTHER
    }
}
