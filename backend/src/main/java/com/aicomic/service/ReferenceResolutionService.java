package com.aicomic.service;

import com.aicomic.entity.AssetItem;
import com.aicomic.entity.Character;
import com.aicomic.entity.Episode;
import com.aicomic.entity.Scene;
import com.aicomic.entity.Script;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.AssetItemRepository;
import com.aicomic.repository.CharacterRepository;
import com.aicomic.repository.EpisodeRepository;
import com.aicomic.repository.SceneRepository;
import com.aicomic.repository.ScriptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色/场景引用解析公共服务
 * 从 StoryboardService 和 DirectorService 中提取，消除重复代码 (DRY)
 *
 * 职责：
 * 1. 解析分镜关联的角色实体（ID 优先，名称回退，批量查询避免 N+1）
 * 2. 解析分镜关联的场景实体（ID 优先，名称回退）
 * 3. 从分镜反查项目 ID（episode → script → project）
 * 4. 收集参考图 URL（角色定妆图 via AssetItem + 场景正面图 + storyboard 自身 referenceImageUrls）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferenceResolutionService {

    private final CharacterRepository characterRepository;
    private final SceneRepository sceneRepository;
    private final EpisodeRepository episodeRepository;
    private final ScriptRepository scriptRepository;
    private final AssetItemRepository assetItemRepository;
    private final ObjectMapper objectMapper;

    // ==================== 项目 ID 反查 ====================

    /**
     * 从分镜反查项目 ID (episode → script → project)
     */
    public Long resolveProjectIdFromStoryboard(Storyboard sb) {
        try {
            return episodeRepository.findById(sb.getEpisodeId())
                    .map(Episode::getScriptId)
                    .flatMap(scriptId -> scriptRepository.findById(scriptId)
                            .map(Script::getProjectId))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("反查项目 ID 失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 角色解析 ====================

    /**
     * 解析分镜关联的角色实体列表
     * 优先使用 involvedCharacterIds（批量查询避免 N+1），回退到 involvedCharacters（名称匹配）
     */
    public List<Character> resolveInvolvedCharacters(Storyboard sb, Long projectId) {
        List<Character> result = new ArrayList<>();

        // 优先按 ID 批量解析（避免 N+1）
        if (sb.getInvolvedCharacterIds() != null && !sb.getInvolvedCharacterIds().isEmpty()) {
            try {
                JsonNode ids = objectMapper.readTree(sb.getInvolvedCharacterIds());
                if (ids.isArray()) {
                    List<Long> idList = new ArrayList<>();
                    for (JsonNode node : ids) {
                        idList.add(node.asLong());
                    }
                    if (!idList.isEmpty()) {
                        result.addAll(characterRepository.findAllById(idList));
                    }
                }
            } catch (Exception e) {
                log.warn("解析 involvedCharacterIds 失败: {}", e.getMessage());
            }
        }

        // 回退: 按名称匹配（如果 ID 解析无结果）
        if (result.isEmpty() && sb.getInvolvedCharacters() != null && !sb.getInvolvedCharacters().isEmpty()) {
            try {
                JsonNode names = objectMapper.readTree(sb.getInvolvedCharacters());
                if (names.isArray() && projectId != null) {
                    List<Character> projectChars = characterRepository.findByProjectIdOrderByNameAsc(projectId);
                    for (JsonNode node : names) {
                        String name = node.asText();
                        projectChars.stream()
                                .filter(ch -> ch.getName().equalsIgnoreCase(name))
                                .findFirst()
                                .ifPresent(result::add);
                    }
                }
            } catch (Exception e) {
                log.warn("按名称匹配角色失败: {}", e.getMessage());
            }
        }

        return result;
    }

    /** 无 projectId 参数版本 — 自动从 storyboard 反查 */
    public List<Character> resolveInvolvedCharacters(Storyboard sb) {
        Long projectId = resolveProjectIdFromStoryboard(sb);
        return resolveInvolvedCharacters(sb, projectId);
    }

    // ==================== 场景解析 ====================

    /**
     * 解析分镜关联的场景实体
     * 优先使用 involvedSceneId，回退到 involvedSceneName（名称匹配）
     */
    public Scene resolveInvolvedScene(Storyboard sb, Long projectId) {
        // 优先按 ID
        if (sb.getInvolvedSceneId() != null) {
            return sceneRepository.findById(sb.getInvolvedSceneId()).orElse(null);
        }

        // 回退: 按名称匹配
        if (sb.getInvolvedSceneName() != null && !sb.getInvolvedSceneName().isEmpty() && projectId != null) {
            List<Scene> projectScenes = sceneRepository.findByProjectIdOrderByNameAsc(projectId);
            return projectScenes.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(sb.getInvolvedSceneName()))
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    /** 无 projectId 参数版本 — 自动从 storyboard 反查 */
    public Scene resolveInvolvedScene(Storyboard sb) {
        Long projectId = resolveProjectIdFromStoryboard(sb);
        return resolveInvolvedScene(sb, projectId);
    }

    // ==================== 参考图 URL 收集 ====================

    /**
     * 收集分镜的所有参考图 URL
     *
     * 优先级策略（当前模型 API 仅支持单参考图）：
     * 1. 场景正面图（frontViewUrl）— 优先级最高，场景对画面构图影响最大
     * 2. 角色定妆图（referenceImageId → AssetItem.filePath）— 角色外貌一致性
     * 3. storyboard 自身 referenceImageUrls — 手动添加的额外参考
     *
     * @return 有序 URL 列表，第一个为最高优先级参考图
     */
    public List<String> collectReferenceImageUrls(Storyboard sb, Long projectId) {
        List<String> urls = new ArrayList<>();

        // 1. 场景正面图（优先级最高）
        Scene scene = resolveInvolvedScene(sb, projectId);
        if (scene != null && scene.getFrontViewUrl() != null && !scene.getFrontViewUrl().isEmpty()) {
            urls.add(scene.getFrontViewUrl());
        }

        // 2. 角色定妆图（via AssetItem）
        List<Character> characters = resolveInvolvedCharacters(sb, projectId);
        List<Long> charRefIds = characters.stream()
                .filter(ch -> ch.getReferenceImageId() != null)
                .map(Character::getReferenceImageId)
                .collect(Collectors.toList());
        if (!charRefIds.isEmpty()) {
            List<AssetItem> assets = assetItemRepository.findByRefCharacterIdInAndType(
                    charRefIds, AssetItem.AssetType.IMAGE);
            for (AssetItem asset : assets) {
                // filePath 是相对路径，构建完整 URL 需要拼接 base-dir
                // 暂时直接返回 filePath，后续可结合文件服务转换为完整 URL
                if (asset.getFilePath() != null && !asset.getFilePath().isEmpty()) {
                    urls.add(asset.getFilePath());
                }
            }
        }

        // 3. storyboard 自身 referenceImageUrls
        if (sb.getReferenceImageUrls() != null && !sb.getReferenceImageUrls().isEmpty()) {
            try {
                JsonNode refUrls = objectMapper.readTree(sb.getReferenceImageUrls());
                if (refUrls.isArray()) {
                    for (JsonNode node : refUrls) {
                        String url = node.asText();
                        if (url != null && !url.isEmpty()) {
                            urls.add(url);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析 referenceImageUrls 失败: {}", e.getMessage());
            }
        }

        return urls;
    }

    /** 无 projectId 参数版本 — 自动从 storyboard 反查 */
    public List<String> collectReferenceImageUrls(Storyboard sb) {
        Long projectId = resolveProjectIdFromStoryboard(sb);
        return collectReferenceImageUrls(sb, projectId);
    }

    // ==================== 场景描述辅助 ====================

    public String getTimeOfDayDescription(Scene.TimeOfDay tod) {
        if (tod == null) return "";
        switch (tod) {
            case MORNING: return "morning light";
            case NOON: return "bright noon";
            case AFTERNOON: return "afternoon warmth";
            case EVENING: return "evening dusk";
            case NIGHT: return "night time, dark";
            case DAWN: return "dawn twilight";
            default: return "";
        }
    }

    public String getWeatherDescription(Scene.Weather w) {
        if (w == null) return "";
        switch (w) {
            case SUNNY: return "clear sky";
            case CLOUDY: return "overcast";
            case RAINY: return "rainy";
            case SNOWY: return "snowy";
            case FOGGY: return "foggy atmosphere";
            default: return "";
        }
    }
}