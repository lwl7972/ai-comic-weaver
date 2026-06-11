package com.aicomic.controller;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.ProjectRequest;
import com.aicomic.entity.PipelineState;
import com.aicomic.entity.Project;
import com.aicomic.repository.*;
import com.aicomic.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目管理 REST API - 对应 6.4.3 项目管理端点
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final PipelineStateRepository pipelineStateRepository;
    private final ProjectService projectService;

    /** POST /api/v1/projects - 创建项目（事务保证项目与流水线状态原子写入） */
    @PostMapping
    @Transactional
    public ApiResponse<Project> create(@RequestBody ProjectRequest req) {
        Project project = new Project();
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        project.setStyle(req.getStyle());
        Project saved = projectRepository.save(project);

        PipelineState state = new PipelineState();
        state.setProjectId(saved.getId());
        state.setCurrentStage(Project.PipelineStage.SCRIPT);
        pipelineStateRepository.save(state);

        return ApiResponse.success(saved);
    }

    /** GET /api/v1/projects - 项目列表 */
    @GetMapping
    public ApiResponse<List<Project>> list() {
        return ApiResponse.success(projectRepository.findAllByOrderByUpdatedAtDesc());
    }

    /** GET /api/v1/projects/{id} - 项目详情 */
    @GetMapping("/{id}")
    public ApiResponse<Project> detail(@PathVariable Long id) {
        return projectRepository.findById(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "项目不存在"));
    }

    /** PUT /api/v1/projects/{id} - 更新项目 */
    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id, @RequestBody ProjectRequest req) {
        return projectRepository.findById(id)
                .map(project -> {
                    if (req.getName() != null) project.setName(req.getName());
                    if (req.getDescription() != null) project.setDescription(req.getDescription());
                    if (req.getStyle() != null) project.setStyle(req.getStyle());
                    return ApiResponse.success(projectRepository.save(project));
                })
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "项目不存在"));
    }

    /** DELETE /api/v1/projects/{id} - 删除项目（级联删除所有关联数据） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            projectService.deleteProject(id);
            return ApiResponse.success();
        } catch (ResourceNotFoundException e) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "项目不存在");
        }
    }

    /** GET /api/v1/projects/{id}/pipeline-state - 流水线状态 */
    @GetMapping("/{id}/pipeline-state")
    public ApiResponse<PipelineState> pipelineState(@PathVariable Long id) {
        return pipelineStateRepository.findByProjectId(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "流水线状态不存在"));
    }

    /** POST /api/v1/pipeline-states/{id}/advance - 推进到下一阶段 */
    @PostMapping("/pipeline-states/advance")
    @Transactional
    public ApiResponse<PipelineState> advanceStage(
            @RequestParam Long projectId,
            @RequestParam String nextStage) {

        // 校验阶段名有效性
        Project.PipelineStage targetStage;
        try {
            targetStage = Project.PipelineStage.valueOf(nextStage);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ApiResponse.PARAM_ERROR, "无效的流水线阶段: " + nextStage);
        }

        return pipelineStateRepository.findByProjectId(projectId)
                .map(state -> {
                    // 校验阶段推进合法性：只允许推进到下一阶段或停留在当前阶段
                    Project.PipelineStage[] stages = Project.PipelineStage.values();
                    int currentIdx = state.getCurrentStage().ordinal();
                    int targetIdx = targetStage.ordinal();
                    if (targetIdx > currentIdx + 1) {
                        return ApiResponse.<PipelineState>error(ApiResponse.PARAM_ERROR,
                                "不能从 " + state.getCurrentStage() + " 跳跃到 " + targetStage + "，请按顺序推进");
                    }

                    state.setCurrentStage(targetStage);
                    // Reset dirty flag for this stage
                    switch (targetStage) {
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
                    }
                    return ApiResponse.success(pipelineStateRepository.save(state));
                })
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "流水线状态不存在"));
    }
}
