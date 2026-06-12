package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.PipelineState;
import com.aicomic.entity.Project;
import com.aicomic.repository.PipelineStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水线脏标记服务 - ADR-20 回退调整机制
 *
 * 核心逻辑：
 *   修改上游模块时自动标记下游为 DIRTY
 *   切换到下游模块前检测脏标记，提示是否重新执行
 *
 * 传播规则（上游修改 → 下游标记DIRTY）：
 *   SCRIPT    → CHARACTER, SCENE, STORYBOARD, DIRECTOR, S_LEVEL(OUTPUT)
 *   CHARACTER → STORYBOARD, DIRECTOR, S_LEVEL(OUTPUT)
 *   SCENE     → STORYBOARD, DIRECTOR, S_LEVEL(OUTPUT)
 *   STORYBOARD → DIRECTOR, S_LEVEL(OUTPUT)
 *   DIRECTOR  → S_LEVEL(OUTPUT)
 *   OUTPUT    → 无
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineStateService {

    private final PipelineStateRepository pipelineStateRepository;

    // ============================================================
    // 查询
    // ============================================================

    /**
     * 获取流水线状态，不存在则初始化
     */
    @Transactional
    public PipelineState getPipelineState(Long projectId) {
        return pipelineStateRepository.findByProjectId(projectId)
                .orElseGet(() -> initPipelineState(projectId));
    }

    /**
     * 初始化流水线状态
     */
    @Transactional
    public PipelineState initPipelineState(Long projectId) {
        return pipelineStateRepository.findByProjectId(projectId)
                .orElseGet(() -> {
                    log.info("初始化流水线状态: projectId={}", projectId);
                    PipelineState state = new PipelineState();
                    state.setProjectId(projectId);
                    state.setCurrentStage(Project.PipelineStage.SCRIPT);
                    return pipelineStateRepository.save(state);
                });
    }

    // ============================================================
    // 脏标记操作
    // ============================================================

    /**
     * 标记下游阶段为 DIRTY
     * 根据传播规则，sourceStage 的修改会导致其下游所有阶段被标记为脏
     */
    @Transactional
    public PipelineState markDirty(Long projectId, Project.PipelineStage sourceStage) {
        PipelineState state = getPipelineState(projectId);

        switch (sourceStage) {
            case SCRIPT:
                state.setCharacterDirty(true);
                state.setSceneDirty(true);
                // fall through 到 CHARACTER 的下游
                state.setStoryboardDirty(true);
                state.setDirectorDirty(true);
                state.setSLevelDirty(true);
                break;
            case CHARACTER:
                state.setStoryboardDirty(true);
                // fall through 到 SCENE 同层下游
                state.setDirectorDirty(true);
                state.setSLevelDirty(true);
                break;
            case SCENE:
                state.setStoryboardDirty(true);
                state.setDirectorDirty(true);
                state.setSLevelDirty(true);
                break;
            case STORYBOARD:
                state.setDirectorDirty(true);
                state.setSLevelDirty(true);
                break;
            case DIRECTOR:
                state.setSLevelDirty(true);
                break;
            case OUTPUT:
                // 无下游，不做任何标记
                break;
            default:
                log.warn("未知的流水线阶段: {}", sourceStage);
        }

        log.info("标记脏标记: projectId={}, sourceStage={}, state={}", projectId, sourceStage, summarizeDirty(state));
        return pipelineStateRepository.save(state);
    }

    /**
     * 清除指定阶段的 DIRTY 标记
     */
    @Transactional
    public PipelineState clearDirtyFlag(Long projectId, Project.PipelineStage stage) {
        PipelineState state = getPipelineState(projectId);

        switch (stage) {
            case SCRIPT:
                state.setScriptDirty(false);
                break;
            case CHARACTER:
                state.setCharacterDirty(false);
                break;
            case SCENE:
                state.setSceneDirty(false);
                break;
            case STORYBOARD:
                state.setStoryboardDirty(false);
                break;
            case DIRECTOR:
                state.setDirectorDirty(false);
                break;
            case OUTPUT:
                state.setSLevelDirty(false);
                break;
            default:
                log.warn("未知的流水线阶段: {}", stage);
        }

        log.info("清除脏标记: projectId={}, stage={}", projectId, stage);
        return pipelineStateRepository.save(state);
    }

    // ============================================================
    // 阶段推进
    // ============================================================

    /**
     * 推进到下一阶段
     * 只允许推进到下一阶段或停留在当前阶段
     * 推进时自动清除目标阶段的 DIRTY 标记
     */
    @Transactional
    public PipelineState advance(Long projectId, Project.PipelineStage targetStage) {
        PipelineState state = getPipelineState(projectId);

        Project.PipelineStage[] stages = Project.PipelineStage.values();
        int currentIdx = state.getCurrentStage().ordinal();
        int targetIdx = targetStage.ordinal();

        // 校验阶段推进合法性：只允许推进到下一阶段或停留在当前阶段
        if (targetIdx > currentIdx + 1) {
            throw new IllegalArgumentException(
                    "不能从 " + state.getCurrentStage() + " 跳跃到 " + targetStage + "，请按顺序推进");
        }

        // 不允许回退（当前实现）
        if (targetIdx < currentIdx) {
            throw new IllegalArgumentException(
                    "不能从 " + state.getCurrentStage() + " 回退到 " + targetStage);
        }

        state.setCurrentStage(targetStage);

        // 推进时自动清除目标阶段的 DIRTY 标记
        clearDirtyByStage(state, targetStage);

        log.info("推进流水线: projectId={}, from={}, to={}", projectId, state.getCurrentStage(), targetStage);
        return pipelineStateRepository.save(state);
    }

    // ============================================================
    // 脏标记查询
    // ============================================================

    /**
     * 检查指定阶段是否有 DIRTY 标记
     */
    public boolean isStageDirty(Long projectId, Project.PipelineStage stage) {
        PipelineState state = getPipelineState(projectId);
        return getDirtyFlag(state, stage);
    }

    /**
     * 获取所有 DIRTY 阶段列表
     */
    public List<Project.PipelineStage> getDirtyStages(Long projectId) {
        PipelineState state = getPipelineState(projectId);
        List<Project.PipelineStage> dirtyStages = new ArrayList<>();

        if (Boolean.TRUE.equals(state.getScriptDirty())) {
            dirtyStages.add(Project.PipelineStage.SCRIPT);
        }
        if (Boolean.TRUE.equals(state.getCharacterDirty())) {
            dirtyStages.add(Project.PipelineStage.CHARACTER);
        }
        if (Boolean.TRUE.equals(state.getSceneDirty())) {
            dirtyStages.add(Project.PipelineStage.SCENE);
        }
        if (Boolean.TRUE.equals(state.getStoryboardDirty())) {
            dirtyStages.add(Project.PipelineStage.STORYBOARD);
        }
        if (Boolean.TRUE.equals(state.getDirectorDirty())) {
            dirtyStages.add(Project.PipelineStage.DIRECTOR);
        }
        if (Boolean.TRUE.equals(state.getSLevelDirty())) {
            dirtyStages.add(Project.PipelineStage.OUTPUT);
        }

        return dirtyStages;
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 读取指定阶段的 dirty 标记
     */
    private boolean getDirtyFlag(PipelineState state, Project.PipelineStage stage) {
        switch (stage) {
            case SCRIPT:
                return Boolean.TRUE.equals(state.getScriptDirty());
            case CHARACTER:
                return Boolean.TRUE.equals(state.getCharacterDirty());
            case SCENE:
                return Boolean.TRUE.equals(state.getSceneDirty());
            case STORYBOARD:
                return Boolean.TRUE.equals(state.getStoryboardDirty());
            case DIRECTOR:
                return Boolean.TRUE.equals(state.getDirectorDirty());
            case OUTPUT:
                return Boolean.TRUE.equals(state.getSLevelDirty());
            default:
                return false;
        }
    }

    /**
     * 清除指定阶段的 dirty 标记（直接修改 state 对象，不单独 save）
     */
    private void clearDirtyByStage(PipelineState state, Project.PipelineStage stage) {
        switch (stage) {
            case SCRIPT:
                state.setScriptDirty(false);
                break;
            case CHARACTER:
                state.setCharacterDirty(false);
                break;
            case SCENE:
                state.setSceneDirty(false);
                break;
            case STORYBOARD:
                state.setStoryboardDirty(false);
                break;
            case DIRECTOR:
                state.setDirectorDirty(false);
                break;
            case OUTPUT:
                state.setSLevelDirty(false);
                break;
            default:
                break;
        }
    }

    /**
     * 生成脏标记摘要字符串（用于日志）
     */
    private String summarizeDirty(PipelineState state) {
        return String.format("script=%s,character=%s,scene=%s,storyboard=%s,director=%s,sLevel=%s",
                state.getScriptDirty(), state.getCharacterDirty(), state.getSceneDirty(),
                state.getStoryboardDirty(), state.getDirectorDirty(), state.getSLevelDirty());
    }
}
