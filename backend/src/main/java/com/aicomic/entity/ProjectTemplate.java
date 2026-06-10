package com.aicomic.entity;

import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.19 项目模板表 - 支持项目复用和快速启动
 */
@Entity
@Table(name = "project_template")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProjectTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** 风格类型 */
    @Enumerated(EnumType.STRING)
    private StyleType style;

    /** 模板数据JSON (包含预设的角色/场景/提示词配置等) */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String templateData;

    /** 是否为系统内置 */
    @Column(name = "is_builtin")
    private Boolean isBuiltin = false;

    /** 使用次数 */
    @Column(name = "use_count")
    private Integer useCount = 0;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum StyleType { SHORT_DRAMA, COMIC, TRAILER, CUSTOM }
}
