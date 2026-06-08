package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.7 角色特征锚点表 - ADR-10 六层身份锚点的具体实现
 *
 * 锚定层次:
 *   IDENTITY     → 身份定位（姓名、职业、社会角色）
 *   APPEARANCE   → 外貌特征（脸型、发型、五官）
 *   COSTUME      → 服装风格（日常装束、标志性服饰）
 *   ACCESSORY    → 道具配饰（随身物品、特殊标记）
 *   POSE         → 姿态动作习惯（站立姿势、手势特征）
 *   EXPRESSION   → 表情神态（常态表情、情绪反应模式）
 */
@Entity
@Table(name = "character_anchor")
@Data
public class CharacterAnchor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnchorLayer layer;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "order_index")
    private Integer orderIndex;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum AnchorLayer {
        IDENTITY,       // 第一层：身份定位
        APPEARANCE,     // 第二层：外貌特征
        COSTUME,        // 第三层：服装风格
        ACCESSORY,      // 第四层：道具配饰
        POSE,           // 第五层：姿态动作习惯
        EXPRESSION      // 第六层：表情神态
    }
}
