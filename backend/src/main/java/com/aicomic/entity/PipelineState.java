package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.15 流水线状态表 - ADR-20 回退调整机制（脏标记）
 *
 * 核心逻辑：
 *   修改上游模块时自动标记下游为 DIRTY
 *   切换到下游模块前检测脏标记，提示是否重新执行
 *
 * 六大模块对应六组 dirty 标记:
 *   scriptDirty → 影响角色/场景/分镜/导演/S级
 *   characterDirty → 影响分镜/导演/S级
 *   sceneDirty → 影响分镜/导演/S级
 *   storyboardDirty → 影响导演/S级
 *   directorDirty → 影响S级
 *   sLevelDirty → 无下游影响
 */
@Entity
@Table(name = "pipeline_state")
@Data
public class PipelineState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    /* ====== 脏标记 (修改上游自动设true，重新执行后设false) ====== */

    @Column(name = "script_dirty")
    private Boolean scriptDirty = false;

    @Column(name = "character_dirty")
    private Boolean characterDirty = false;

    @Column(name = "scene_dirty")
    private Boolean sceneDirty = false;

    @Column(name = "storyboard_dirty")
    private Boolean storyboardDirty = false;

    @Column(name = "director_dirty")
    private Boolean directorDirty = false;

    @Column(name = "s_level_dirty")
    private Boolean sLevelDirty = false;

    /* ====== 当前所处阶段 ====== */

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false)
    private Project.PipelineStage currentStage = Project.PipelineStage.SCRIPT;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
