package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.28 标签表 - 支持素材和资源的分类管理
 */
@Entity
@Table(name = "tag")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** 标签颜色 (hex code) */
    @Column(length = 10)
    private String color = "#409eff";

    /** 标签分类 */
    @Column(length = 50)
    private String category;

    /** 使用次数 */
    @Column(name = "use_count")
    private Integer useCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
