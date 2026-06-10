package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.Episode;
import com.aicomic.entity.PipelineState;
import com.aicomic.entity.Project;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目管理 REST API - 对应 6.4.3 项目管理端点
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final PipelineStateRepository pipelineStateRepository;
    private final ScriptRepository scriptRepository;
    private final NovelRepository novelRepository;
    private final CharacterRepository characterRepository;
    private final SceneRepository sceneRepository;
    private final EpisodeRepository episodeRepository;
    private final StoryboardRepository storyboardRepository;
    private final GenerationTaskRepository generationTaskRepository;

    /** POST /api/v1/projects - 创建项目（事务保证项目与流水线状态原子写入） */
    @PostMapping
    @Transactional
    public ApiResponse<Project> create(@RequestBody Project project) {
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

    /** DELETE /api/v1/projects/{id} - 删除项目（级联删除所有关联数据） */
    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!projectRepository.existsById(id)) {
            return ApiResponse.error(ApiResponse.NOT_FOUND, "项目不存在");
        }

        // 1. 先收集关联 ID（用于清理生成任务）
        List<Long> charIds = characterRepository.findByProjectIdOrderByNameAsc(id)
                .stream().map(c -> c.getId()).collect(Collectors.toList());
        List<Long> sceneIds = sceneRepository.findByProjectIdOrderByNameAsc(id)
                .stream().map(s -> s.getId()).collect(Collectors.toList());

        // 2. 删除剧本 → 剧集 → 分镜 级联
        List<Long> scriptIds = scriptRepository.findByProjectIdOrderByCreatedAtDesc(id)
                .stream().map(s -> s.getId()).collect(Collectors.toList());
        List<Long> allEpisodeIds = scriptIds.stream()
                .flatMap(sid -> episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(sid).stream())
                .map(Episode::getId).collect(Collectors.toList());
        List<Long> storyboardIds = allEpisodeIds.stream()
                .flatMap(eid -> storyboardRepository.findByEpisodeIdOrderBySequenceAsc(eid).stream())
                .map(sb -> sb.getId()).collect(Collectors.toList());

        // 3. 按依赖顺序删除：分镜 → 剧集 → 生成任务 → 角色/场景/小说/剧本 → 流水线 → 项目
        for (Long eid : allEpisodeIds) {
            storyboardRepository.deleteByEpisodeId(eid);
        }
        if (!storyboardIds.isEmpty()) {
            generationTaskRepository.deleteByTargetTypeAndTargetIdIn("STORYBOARD", storyboardIds);
        }
        if (!allEpisodeIds.isEmpty()) {
            episodeRepository.deleteAllById(allEpisodeIds);
        }
        if (!charIds.isEmpty()) {
            generationTaskRepository.deleteByTargetTypeAndTargetIdIn("CHARACTER", charIds);
            generationTaskRepository.deleteByTargetTypeAndTargetIdIn("CHARACTER_ANCHOR", charIds);
        }
        if (!sceneIds.isEmpty()) {
            generationTaskRepository.deleteByTargetTypeAndTargetIdIn("SCENE", sceneIds);
        }
        characterRepository.deleteByProjectId(id);
        sceneRepository.deleteByProjectId(id);
        novelRepository.deleteByProjectId(id);
        scriptRepository.deleteByProjectId(id);

        // 4. 删除流水线状态
        pipelineStateRepository.findByProjectId(id).ifPresent(pipelineStateRepository::delete);

        // 5. 最后删除项目本身
        projectRepository.deleteById(id);
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
