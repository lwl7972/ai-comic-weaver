package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.dto.StoryboardRequest;
import com.aicomic.entity.Character;
import com.aicomic.entity.Episode;
import com.aicomic.entity.ModelConfig;
import com.aicomic.entity.Scene;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.CharacterRepository;
import com.aicomic.repository.SceneRepository;
import com.aicomic.repository.StoryboardRepository;
import com.aicomic.repository.EpisodeRepository;
import com.aicomic.service.model.ModelCallException;
import com.aicomic.service.model.ModelCallService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 🎬 分镜模块服务
 * 负责：三步分镜流程（解析→编辑→生成，ADR-19）、专业电影级参数管理、分镜图批量生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardService {

    private final StoryboardRepository storyboardRepository;
    private final EpisodeRepository episodeRepository;
    private final CharacterRepository characterRepository;
    private final SceneRepository sceneRepository;
    private final ModelCallService modelCallService;
    private final ReferenceResolutionService refService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    // ==================== Basic CRUD ====================

    @Transactional(readOnly = true)
    public List<Storyboard> getStoryboardsByEpisode(Long episodeId) {
        return storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
    }

    @Transactional(readOnly = true)
    public Storyboard findStoryboardById(Long id) {
        return storyboardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("分镜", id));
    }

    @Transactional
    public Storyboard saveStoryboard(Storyboard storyboard) {
        return storyboardRepository.save(storyboard);
    }

    @Transactional
    public List<Storyboard> saveStoryboards(List<Storyboard> storyboards) {
        return storyboardRepository.saveAll(storyboards);
    }

    @Transactional
    public void deleteStoryboard(Long storyboardId) {
        if (!storyboardRepository.existsById(storyboardId)) {
            throw new ResourceNotFoundException("分镜", storyboardId);
        }
        storyboardRepository.deleteById(storyboardId);
    }

    // ==================== 三步流程 Step 1: AI Parse (ADR-19) ====================

    /**
     * AI 解析剧本为结构化分镜数据（异步执行）
     * 三步流程第一步：解析剧本 → 结构化数据（场景列表+对白列表+动作列表）
     */
    @Async("taskExecutor")
    public void parseScriptToStoryboardAsync(Long episodeId) {
        log.info("开始 AI 解析剧集为分镜: episodeId={}", episodeId);
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("剧集", episodeId));

        try {
            if (episode.getScriptContent() == null || episode.getScriptContent().isEmpty()) {
                sseService.pushNotification("storyboard-error", "剧集无剧本内容，解析失败");
                return;
            }

            sseService.pushNotification("storyboard-progress",
                    String.format("正在解析第%s集剧本为分镜数据...", episode.getEpisodeNumber()));

            String prompt = buildStoryboardParsePrompt(episode);
            String result = modelCallService.callText(ModelConfig.ModelType.TEXT, prompt);

            List<Storyboard> storyboards = parseStoryboardResult(result, episodeId);
            if (storyboards.isEmpty()) {
                sseService.pushNotification("storyboard-error", "解析未产生有效分镜数据");
                return;
            }

            // Delete existing storyboards for this episode and save new ones
            storyboardRepository.deleteByEpisodeId(episodeId);
            storyboardRepository.saveAll(storyboards);

            // Mark episode as parsed
            episode.setStatus(Episode.EpisodeStatus.PARSED);
            episodeRepository.save(episode);

            sseService.pushNotification("storyboard-completed",
                    String.format("解析完成: 生成了 %d 个分镜", storyboards.size()));
            log.info("剧本解析完成: episodeId={}, 分镜数={}", episodeId, storyboards.size());

        } catch (ModelCallException e) {
            log.error("剧本解析失败: {}", e.getMessage(), e);
            sseService.pushNotification("storyboard-error", "剧本解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("剧本解析异常: {}", e.getMessage(), e);
            sseService.pushNotification("storyboard-error", "剧本解析异常: " + e.getMessage());
        }
    }

    // ==================== 三步流程 Step 2: Batch Edit ====================

    /**
     * 批量编辑分镜（三步流程第二步）
     */
    @Transactional
    public List<Storyboard> batchUpdateStoryboards(List<StoryboardRequest> requests) {
        List<Storyboard> updated = new ArrayList<>();
        for (StoryboardRequest req : requests) {
            Storyboard sb;
            if (req.getId() != null) {
                sb = findStoryboardById(req.getId());
            } else {
                sb = new Storyboard();
                sb.setEpisodeId(req.getEpisodeId());
            }
            applyRequestToStoryboard(req, sb);
            updated.add(storyboardRepository.save(sb));
        }
        return updated;
    }

    // ==================== 三步流程 Step 3: Image Generation ====================

    /**
     * 批量生成分镜图（异步执行）
     * 三步流程第三步：根据分镜参数生成图片
     */
    @Async("taskExecutor")
    public void generateStoryboardImagesAsync(Long episodeId) {
        log.info("开始批量生成分镜图: episodeId={}", episodeId);
        List<Storyboard> storyboards = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);

        if (storyboards.isEmpty()) {
            sseService.pushNotification("storyboard-error", "没有找到分镜数据");
            return;
        }

        int total = storyboards.size();
        int success = 0;
        int failed = 0;

        try {
            for (int i = 0; i < storyboards.size(); i++) {
                Storyboard sb = storyboards.get(i);
                sseService.pushNotification("storyboard-progress",
                        String.format("正在生成分镜图 (%d/%d)...", i + 1, total));

                try {
                    sb.setStatus(Storyboard.StoryboardStatus.IMAGE_GENERATING);
                    storyboardRepository.save(sb);

                    // 预解析 projectId，避免后续多次反查
                    Long projectId = refService.resolveProjectIdFromStoryboard(sb);
                    String imagePrompt = buildStoryboardImagePrompt(sb, projectId);
                    List<String> refUrls = refService.collectReferenceImageUrls(sb, projectId);
                    String referenceImageUrl = refUrls.isEmpty() ? null : refUrls.get(0);
                    String imageUrl = modelCallService.callImage(imagePrompt, referenceImageUrl, null);

                    sb.setGeneratedImageUrl(imageUrl);
                    sb.setStatus(Storyboard.StoryboardStatus.IMAGE_DONE);
                    storyboardRepository.save(sb);
                    success++;
                } catch (Exception e) {
                    log.error("分镜图生成失败: storyboardId={}, error={}", sb.getId(), e.getMessage());
                    sb.setStatus(Storyboard.StoryboardStatus.ERROR);
                    storyboardRepository.save(sb);
                    failed++;
                }
            }

            sseService.pushNotification("storyboard-completed",
                    String.format("分镜图生成完成: %d成功, %d失败", success, failed));
            log.info("分镜图批量生成完成: episodeId={}, 成功={}, 失败={}", episodeId, success, failed);

        } catch (Exception e) {
            log.error("分镜图批量生成异常: {}", e.getMessage(), e);
            sseService.pushNotification("storyboard-error", "分镜图生成异常: " + e.getMessage());
        }
    }

    /**
     * 单分镜重新生成图片
     */
    @Async("taskExecutor")
    public void generateSingleStoryboardImageAsync(Long storyboardId) {
        log.info("开始重新生成单分镜图: storyboardId={}", storyboardId);
        Storyboard sb = storyboardRepository.findById(storyboardId)
                .orElseThrow(() -> new ResourceNotFoundException("分镜", storyboardId));

        try {
            sseService.pushNotification("storyboard-progress",
                    String.format("正在重新生成分镜 #%d 的图片...", sb.getSequence() + 1));

            sb.setStatus(Storyboard.StoryboardStatus.IMAGE_GENERATING);
            storyboardRepository.save(sb);

            Long projectId = refService.resolveProjectIdFromStoryboard(sb);
            String imagePrompt = buildStoryboardImagePrompt(sb, projectId);
            List<String> refUrls = refService.collectReferenceImageUrls(sb, projectId);
            String referenceImageUrl = refUrls.isEmpty() ? null : refUrls.get(0);
            String imageUrl = modelCallService.callImage(imagePrompt, referenceImageUrl, null);

            sb.setGeneratedImageUrl(imageUrl);
            sb.setStatus(Storyboard.StoryboardStatus.IMAGE_DONE);
            storyboardRepository.save(sb);

            sseService.pushNotification("storyboard-completed",
                    String.format("分镜 #%d 图片重新生成完成", sb.getSequence() + 1));
            log.info("单分镜图重新生成完成: storyboardId={}", storyboardId);

        } catch (ModelCallException e) {
            log.error("单分镜图生成失败: {}", e.getMessage(), e);
            sb.setStatus(Storyboard.StoryboardStatus.ERROR);
            storyboardRepository.save(sb);
            sseService.pushNotification("storyboard-error", "分镜图生成失败: " + e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    private String buildStoryboardParsePrompt(Episode episode) {
        return "你是一名专业的漫剧分镜师。请将以下剧集剧本解析为结构化分镜数据。\n\n"
                + "每个分镜输出以下 JSON 格式：\n"
                + "```json\n"
                + "{\n"
                + "  \"sequence\": 从0开始的序号,\n"
                + "  \"timeRange\": \"如 '0-4s' 的时间预估\",\n"
                + "  \"continuity\": \"承接上镜描述\",\n"
                + "  \"dialogue\": \"[角色名, 情绪]:'台词' 格式\",\n"
                + "  \"action\": \"动作描述\",\n"
                + "  \"emotion\": \"情绪/氛围标签\",\n"
                + "  \"shotSize\": \"EXTREME_CLOSE_UP/CLOSE_UP/MEDIUM_CLOSE_UP/MEDIUM/MEDIUM_WIDE/WIDE/EXTREME_WIDE\",\n"
                + "  \"cameraAngle\": \"EYE_LEVEL/HIGH_ANGLE/LOW_ANGLE/BIRD_EYE/DUTCH_ANGLE\",\n"
                + "  \"cameraMovement\": \"STATIC/PAN_LEFT/PAN_RIGHT/TILT_UP/TILT_DOWN/ZOOM_IN/ZOOM_OUT/TRACKING/CRANE/HANDHELD\",\n"
                + "  \"involvedCharacters\": [\"角色名列表\"],\n"
                + "  \"involvedSceneName\": \"所在场景名称\",\n"
                + "  \"bgSound\": \"背景音效建议\"\n"
                + "}\n"
                + "```\n\n"
                + "景别选择指南：EXTREME_CLOSE_UP(特写-面部细节)/CLOSE_UP(近景-肩以上)/MEDIUM_CLOSE_UP(中近景-腰部以上)/"
                + "MEDIUM(中景-全身)/MEDIUM_WIDE(中远景-环境可见)/WIDE(远景-大环境)/EXTREME_WIDE(大远景-宏大场景)\n\n"
                + "=== 剧集剧本 ===\n"
                + String.format("第%s集: %s\n", episode.getEpisodeNumber(), episode.getTitle())
                + episode.getScriptContent() + "\n\n"
                + "=== 请输出分镜列表（JSON数组格式，用```json和```包裹）===";
    }

    private List<Storyboard> parseStoryboardResult(String result, Long episodeId) {
        List<Storyboard> storyboards = new ArrayList<>();
        try {
            String jsonStr = result;
            int startIdx = result.indexOf("```json");
            if (startIdx >= 0) {
                startIdx += 7;
                int endIdx = result.indexOf("```", startIdx);
                if (endIdx > startIdx) {
                    jsonStr = result.substring(startIdx, endIdx).trim();
                }
            } else if (result.contains("[")) {
                startIdx = result.indexOf("[");
                int endIdx = result.lastIndexOf("]") + 1;
                jsonStr = result.substring(startIdx, endIdx);
            }

            if (jsonStr.startsWith("[")) {
                JsonNode array = objectMapper.readTree(jsonStr);
                if (array.isArray()) {
                    for (int i = 0; i < array.size(); i++) {
                        Storyboard sb = storyboardFromJsonNode(array.get(i), episodeId, i);
                        storyboards.add(sb);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("分镜 JSON 解析失败: {}", e.getMessage());
        }

        return storyboards;
    }

    private Storyboard storyboardFromJsonNode(JsonNode node, Long episodeId, int index) {
        Storyboard sb = new Storyboard();
        sb.setEpisodeId(episodeId);
        sb.setSequence(node.has("sequence") ? node.get("sequence").asInt() : index);
        sb.setTimeRange(node.has("timeRange") ? node.get("timeRange").asText() : index + "~" + (index + 4) + "s");
        sb.setContinuity(node.has("continuity") ? node.get("continuity").asText() : "");
        sb.setDialogue(node.has("dialogue") ? node.get("dialogue").asText() : "");
        sb.setAction(node.has("action") ? node.get("action").asText() : "");
        sb.setEmotion(node.has("emotion") ? node.get("emotion").asText() : "中性");
        sb.setShotSize(parseEnumSafe(node, "shotSize", Storyboard.ShotSize.MEDIUM));
        sb.setCameraAngle(parseEnumSafe(node, "cameraAngle", Storyboard.CameraAngle.EYE_LEVEL));
        sb.setCameraMovement(parseEnumSafe(node, "cameraMovement", Storyboard.CameraMovement.STATIC));

        if (node.has("involvedCharacters")) {
            JsonNode chars = node.get("involvedCharacters");
            if (chars.isArray()) {
                sb.setInvolvedCharacters(chars.toString());
            } else {
                sb.setInvolvedCharacters("[\"" + chars.asText() + "\"]");
            }
        }

        sb.setInvolvedSceneName(node.has("involvedSceneName") ? node.get("involvedSceneName").asText() : "");
        sb.setBgSound(node.has("bgSound") ? node.get("bgSound").asText() : "");
        sb.setGenerationPurpose(Storyboard.GenerationPurpose.STORYBOARD_IMAGE);
        sb.setStatus(Storyboard.StoryboardStatus.PENDING);
        sb.setCreatedAt(LocalDateTime.now());
        sb.setUpdatedAt(LocalDateTime.now());
        return sb;
    }

    private <E extends Enum<E>> E parseEnumSafe(JsonNode node, String field, E defaultValue) {
        try {
            if (node.has(field)) {
                @SuppressWarnings("unchecked")
                Class<E> clazz = (Class<E>) defaultValue.getClass();
                return Enum.valueOf(clazz, node.get(field).asText());
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }

    /**
     * 构建分镜图生成提示词（注入角色锚点描述和场景信息）
     * 使用预解析的 projectId 避免多次反查数据库
     */
    private String buildStoryboardImagePrompt(Storyboard sb, Long projectId) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Comic manga panel, ");

        // Add shot size guidance
        if (sb.getShotSize() != null) {
            prompt.append(getShotSizeDescription(sb.getShotSize())).append(", ");
        }

        // Add camera angle guidance
        if (sb.getCameraAngle() != null) {
            prompt.append(getCameraAngleDescription(sb.getCameraAngle())).append(", ");
        }

        // Inject character references (anchor prompt descriptions) — 使用预解析 projectId
        List<Character> characters = refService.resolveInvolvedCharacters(sb, projectId);
        for (Character ch : characters) {
            if (ch.getAnchorPrompt() != null && !ch.getAnchorPrompt().isEmpty()) {
                prompt.append("character [").append(ch.getName()).append("]: ").append(ch.getAnchorPrompt()).append(", ");
            } else if (ch.getAppearance() != null && !ch.getAppearance().isEmpty()) {
                prompt.append("character [").append(ch.getName()).append("]: ").append(ch.getAppearance()).append(", ");
            }
        }

        // Inject scene reference (description + style + timeOfDay) — 使用预解析 projectId
        Scene scene = refService.resolveInvolvedScene(sb, projectId);
        if (scene != null) {
            prompt.append("scene [").append(scene.getName()).append("]: ");
            if (scene.getDescription() != null && !scene.getDescription().isEmpty()) {
                prompt.append(scene.getDescription()).append(", ");
            }
            if (scene.getStyleHint() != null && !scene.getStyleHint().isEmpty()) {
                prompt.append(scene.getStyleHint()).append(", ");
            }
            if (scene.getTimeOfDay() != null) {
                prompt.append(refService.getTimeOfDayDescription(scene.getTimeOfDay())).append(", ");
            }
            if (scene.getWeather() != null) {
                prompt.append(refService.getWeatherDescription(scene.getWeather())).append(", ");
            }
        } else if (sb.getInvolvedSceneName() != null && !sb.getInvolvedSceneName().isEmpty()) {
            prompt.append("scene: ").append(sb.getInvolvedSceneName()).append(", ");
        }

        // Add action
        if (sb.getAction() != null && !sb.getAction().isEmpty()) {
            prompt.append(sb.getAction()).append(", ");
        }

        // Add emotion/atmosphere
        if (sb.getEmotion() != null && !sb.getEmotion().isEmpty()) {
            prompt.append(sb.getEmotion()).append(" atmosphere, ");
        }

        prompt.append("high quality, anime style, detailed, professional manga art");
        return prompt.toString();
    }

    private String getShotSizeDescription(Storyboard.ShotSize ss) {
        switch (ss) {
            case EXTREME_CLOSE_UP: return "extreme close-up, facial detail";
            case CLOSE_UP: return "close-up shot, shoulders up";
            case MEDIUM_CLOSE_UP: return "medium close-up, waist up";
            case MEDIUM: return "medium shot, full body";
            case MEDIUM_WIDE: return "medium wide shot, character and environment";
            case WIDE: return "wide shot, full scene";
            case EXTREME_WIDE: return "extreme wide shot, grand landscape";
            default: return "";
        }
    }

    private String getCameraAngleDescription(Storyboard.CameraAngle ca) {
        switch (ca) {
            case EYE_LEVEL: return "eye level angle";
            case HIGH_ANGLE: return "high angle looking down";
            case LOW_ANGLE: return "low angle looking up";
            case BIRD_EYE: return "bird's eye view";
            case DUTCH_ANGLE: return "dutch angle, tilted";
            default: return "";
        }
    }

    /**
     * 统一的请求→实体映射方法（含枚举异常保护）
     * Controller 和 Service 统一使用此方法，避免职责重复
     */
    public void applyRequestToStoryboard(StoryboardRequest req, Storyboard sb) {
        if (req.getSequence() != null) sb.setSequence(req.getSequence());
        if (req.getTimeRange() != null) sb.setTimeRange(req.getTimeRange());
        if (req.getContinuity() != null) sb.setContinuity(req.getContinuity());
        if (req.getDialogue() != null) sb.setDialogue(req.getDialogue());
        if (req.getAction() != null) sb.setAction(req.getAction());
        if (req.getEmotion() != null) sb.setEmotion(req.getEmotion());
        if (req.getShotSize() != null) {
            try { sb.setShotSize(Storyboard.ShotSize.valueOf(req.getShotSize())); }
            catch (IllegalArgumentException e) { log.warn("无效的景别枚举值: {}", req.getShotSize()); }
        }
        if (req.getCameraAngle() != null) {
            try { sb.setCameraAngle(Storyboard.CameraAngle.valueOf(req.getCameraAngle())); }
            catch (IllegalArgumentException e) { log.warn("无效的机位枚举值: {}", req.getCameraAngle()); }
        }
        if (req.getCameraMovement() != null) {
            try { sb.setCameraMovement(Storyboard.CameraMovement.valueOf(req.getCameraMovement())); }
            catch (IllegalArgumentException e) { log.warn("无效的运镜枚举值: {}", req.getCameraMovement()); }
        }
        if (req.getInvolvedCharacters() != null) sb.setInvolvedCharacters(req.getInvolvedCharacters());
        if (req.getInvolvedCharacterIds() != null) sb.setInvolvedCharacterIds(req.getInvolvedCharacterIds());
        if (req.getInvolvedSceneName() != null) sb.setInvolvedSceneName(req.getInvolvedSceneName());
        if (req.getInvolvedSceneId() != null) sb.setInvolvedSceneId(req.getInvolvedSceneId());
        if (req.getReferenceImageUrls() != null) sb.setReferenceImageUrls(req.getReferenceImageUrls());
        if (req.getBgSound() != null) sb.setBgSound(req.getBgSound());
        if (req.getGenerationPurpose() != null) {
            try { sb.setGenerationPurpose(Storyboard.GenerationPurpose.valueOf(req.getGenerationPurpose())); }
            catch (IllegalArgumentException e) { log.warn("无效的生成用途枚举值: {}", req.getGenerationPurpose()); }
        }
    }

    // ==================== 角色/场景引用解析 ====================

    /**
     * 自动解析分镜的角色/场景引用（名称→ID）
     * 委托 ReferenceResolutionService，预解析 projectId 避免多次反查
     */
    @Transactional
    public Storyboard resolveReferences(Long storyboardId) {
        Storyboard sb = findStoryboardById(storyboardId);
        Long projectId = refService.resolveProjectIdFromStoryboard(sb);

        try {
            // 解析角色名 → ID（使用批量查询）
            if (sb.getInvolvedCharacterIds() == null || sb.getInvolvedCharacterIds().isEmpty()) {
                List<Long> charIds = new ArrayList<>();
                if (sb.getInvolvedCharacters() != null && !sb.getInvolvedCharacters().isEmpty()) {
                    JsonNode names = objectMapper.readTree(sb.getInvolvedCharacters());
                    if (names.isArray() && projectId != null) {
                        List<Character> projectChars = characterRepository.findByProjectIdOrderByNameAsc(projectId);
                        for (JsonNode node : names) {
                            String name = node.asText();
                            projectChars.stream()
                                    .filter(ch -> ch.getName().equalsIgnoreCase(name))
                                    .findFirst()
                                    .ifPresent(ch -> charIds.add(ch.getId()));
                        }
                    }
                }
                if (!charIds.isEmpty()) {
                    sb.setInvolvedCharacterIds(objectMapper.writeValueAsString(charIds));
                }
            }

            // 解析场景名 → ID
            if (sb.getInvolvedSceneId() == null && sb.getInvolvedSceneName() != null
                    && !sb.getInvolvedSceneName().isEmpty() && projectId != null) {
                List<Scene> projectScenes = sceneRepository.findByProjectIdOrderByNameAsc(projectId);
                projectScenes.stream()
                        .filter(s -> s.getName().equalsIgnoreCase(sb.getInvolvedSceneName()))
                        .findFirst()
                        .ifPresent(s -> sb.setInvolvedSceneId(s.getId()));
            }

            // 收集参考图 URL（含角色定妆图 via AssetItem）
            List<String> refUrls = refService.collectReferenceImageUrls(sb, projectId);
            if (!refUrls.isEmpty()) {
                sb.setReferenceImageUrls(objectMapper.writeValueAsString(refUrls));
            }
        } catch (Exception e) {
            log.warn("解析引用异常: {}", e.getMessage());
        }

        return storyboardRepository.save(sb);
    }
}