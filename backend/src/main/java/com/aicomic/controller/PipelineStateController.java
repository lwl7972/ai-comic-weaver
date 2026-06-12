package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.PipelineAdvanceRequest;
import com.aicomic.entity.PipelineState;
import com.aicomic.entity.Project;
import com.aicomic.service.PipelineStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 流水线状态管理 REST API - ADR-20 脏标记机制
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class PipelineStateController {

    private final PipelineStateService pipelineStateService;

    /**
     * GET /api/v1/projects/{projectId}/pipeline-state
     * 获取流水线状态（不存在则自动初始化）
     */
    @GetMapping("/{projectId}/pipeline-state")
    public ApiResponse<PipelineState> getPipelineState(@PathVariable Long projectId) {
        return ApiResponse.success(pipelineStateService.getPipelineState(projectId));
    }

    /**
     * POST /api/v1/projects/{projectId}/pipeline-advance
     * 推进到下一阶段
     */
    @PostMapping("/{projectId}/pipeline-advance")
    public ApiResponse<PipelineState> advance(
            @PathVariable Long projectId,
            @Valid @RequestBody PipelineAdvanceRequest request) {
        try {
            PipelineState state = pipelineStateService.advance(projectId, request.getTargetStage());
            return ApiResponse.success(state);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ApiResponse.PARAM_ERROR, e.getMessage());
        }
    }

    /**
     * POST /api/v1/projects/{projectId}/pipeline-clear-dirty?stage=XXX
     * 清除指定阶段的 DIRTY 标记
     */
    @PostMapping("/{projectId}/pipeline-clear-dirty")
    public ApiResponse<PipelineState> clearDirtyFlag(
            @PathVariable Long projectId,
            @RequestParam Project.PipelineStage stage) {
        return ApiResponse.success(pipelineStateService.clearDirtyFlag(projectId, stage));
    }

    /**
     * GET /api/v1/projects/{projectId}/pipeline-dirty-stages
     * 获取所有 DIRTY 阶段列表
     */
    @GetMapping("/{projectId}/pipeline-dirty-stages")
    public ApiResponse<List<Project.PipelineStage>> getDirtyStages(@PathVariable Long projectId) {
        return ApiResponse.success(pipelineStateService.getDirtyStages(projectId));
    }
}
