package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.Episode;
import com.aicomic.entity.Project;
import com.aicomic.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目管理服务 - 业务逻辑层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final PipelineStateRepository pipelineStateRepository;
    private final ScriptRepository scriptRepository;
    private final NovelRepository novelRepository;
    private final CharacterRepository characterRepository;
    private final SceneRepository sceneRepository;
    private final EpisodeRepository episodeRepository;
    private final StoryboardRepository storyboardRepository;
    private final GenerationTaskRepository generationTaskRepository;

    /**
     * 删除项目及其所有关联数据（级联删除）
     * 按依赖顺序删除：分镜 → 剧集 → 生成任务 → 角色/场景/小说/剧本 → 流水线 → 项目
     */
    @Transactional
    public void deleteProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("项目", projectId);
        }

        log.info("开始级联删除项目: projectId={}", projectId);

        // 1. 先收集关联 ID（用于清理生成任务）
        List<Long> charIds = characterRepository.findByProjectIdOrderByNameAsc(projectId)
                .stream().map(c -> c.getId()).collect(Collectors.toList());
        List<Long> sceneIds = sceneRepository.findByProjectIdOrderByNameAsc(projectId)
                .stream().map(s -> s.getId()).collect(Collectors.toList());

        // 2. 删除剧本 → 剧集 → 分镜 级联
        List<Long> scriptIds = scriptRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(s -> s.getId()).collect(Collectors.toList());
        List<Long> allEpisodeIds = scriptIds.stream()
                .flatMap(sid -> episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(sid).stream())
                .map(Episode::getId).collect(Collectors.toList());
        List<Long> storyboardIds = allEpisodeIds.stream()
                .flatMap(eid -> storyboardRepository.findByEpisodeIdOrderBySequenceAsc(eid).stream())
                .map(sb -> sb.getId()).collect(Collectors.toList());

        // 3. 按依赖顺序删除
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
        characterRepository.deleteByProjectId(projectId);
        sceneRepository.deleteByProjectId(projectId);
        novelRepository.deleteByProjectId(projectId);
        scriptRepository.deleteByProjectId(projectId);

        // 4. 删除流水线状态
        pipelineStateRepository.findByProjectId(projectId).ifPresent(pipelineStateRepository::delete);

        // 5. 最后删除项目本身
        projectRepository.deleteById(projectId);
        log.info("项目级联删除完成: projectId={}", projectId);
    }
}
