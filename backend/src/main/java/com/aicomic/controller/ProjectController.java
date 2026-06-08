package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.PipelineState;
import com.aicomic.entity.Project;
import com.aicomic.repository.PipelineStateRepository;
import com.aicomic.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目管理 REST API - 对应 6.4.3 项目管理端点
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final PipelineStateRepository pipelineStateRepository;

    /** POST /api/v1/projects - 创建项目 */
    @PostMapping
    public ApiResponse<Project> create(@RequestBody Project project) {
        // Initialize pipeline state alongside project
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
    public ApiResponse<Project> update(@PathVariable Long id, @RequestBody Project updated) {
        return projectRepository.findById(id)
                .map(project -> {
                    if (updated.getName() != null) project.setName(updated.getName());
                    if (updated.getDescription() != null) project.setDescription(updated.getDescription());
                    if (updated.getStyle() != null) project.setStyle(updated.getStyle());
                    return ApiResponse.success(projectRepository.save(project));
                })
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "项目不存在"));
    }

    /** DELETE /api/v1/projects/{id} - 删除项目 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!projectRepository.existsById(id)) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "项目不存在");
        }
        projectRepository.deleteById(id);
        pipelineStateRepository.findByProjectId(id).ifPresent(pipelineStateRepository::delete);
        return ApiResponse.success();
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
    public ApiResponse<PipelineState> advanceStage(
            @RequestParam Long projectId,
            @RequestParam String nextStage) {
        return pipelineStateRepository.findByProjectId(projectId)
                .map(state -> {
                    state.setCurrentStage(Project.PipelineStage.valueOf(nextStage));
                    // Reset dirty flag for this stage
                    switch (nextStage) {
                        case "CHARACTER" -> state.setCharacterDirty(false);
                        case "SCENE" -> state.setSceneDirty(false);
                        case "STORYBOARD" -> state.setStoryboardDirty(false);
                        case "DIRECTOR" -> state.setDirectorDirty(false);
                        case "OUTPUT" -> state.setSLevelDirty(false);
                    }
                    return ApiResponse.success(pipelineStateRepository.save(state));
                })
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "流水线状态不存在"));
    }
}
