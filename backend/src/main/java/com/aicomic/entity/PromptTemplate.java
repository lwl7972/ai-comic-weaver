package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.13 提示词模板表 - 管理 A.1~A.8 所有提示词模板版本
 *
 * 分类: SCRIPT / CHARACTER / SCENE / STORYBOARD / DIRECTOR / SYSTEM
 */
@Entity
@Table(name = "prompt_template")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateCategory category;

    /**
     * 提示词模板内容，支持占位符变量
     * 如: {scriptContent}, {characterList}, {sceneList} 等
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 变量列表 (JSON数组) */
    @Column(columnDefinition = "TEXT")
    private String variables;      // e.g., ["scriptContent","characterList","sceneList"]

    /** 版本号 (支持迭代更新) */
    @Column(nullable = false)
    private Integer version = 1;

    /** 是否为默认模板 */
    @Column(name = "is_default")
    private Boolean isDefault = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum TemplateCategory {
        SCRIPT,      // A.1 小说转剧本, A.4 剧本生成分镜提示词, A.5 大纲生成, A.8.1 剧本解析
        CHARACTER,   // A.3 定妆图提示词
        SCENE,       // A.7 场景四视图
        STORYBOARD,  // A.2 视觉风格提示词, A.6 资产提取
        DIRECTOR,    // A.9 视频生成提示词
        SYSTEM       // A.8.2 分章摘要, 其他系统级提示词
    }
}
