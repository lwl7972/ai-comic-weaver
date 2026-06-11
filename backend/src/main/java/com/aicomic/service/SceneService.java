package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.Episode;
import com.aicomic.entity.ExtractedAsset;
import com.aicomic.entity.ModelConfig;
import com.aicomic.entity.Scene;
import com.aicomic.entity.Script;
import com.aicomic.repository.EpisodeRepository;
import com.aicomic.repository.ExtractedAssetRepository;
import com.aicomic.repository.SceneRepository;
import com.aicomic.repository.ScriptRepository;
import com.aicomic.service.model.ModelCallException;
import com.aicomic.service.model.ModelCallService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 🌄 场景模块服务
 * 负责：场景创建管理、AI自动提取场景资产（ADR-4）、四视图生成（ADR-11）、场景风格一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SceneService {

    private final SceneRepository sceneRepository;
    private final ExtractedAssetRepository extractedAssetRepository;
    private final ScriptRepository scriptRepository;
    private final EpisodeRepository episodeRepository;
    private final ModelCallService modelCallService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    // ==================== Basic CRUD ====================

    @Transactional(readOnly = true)
    public List<Scene> getScenesByProject(Long projectId) {
        return sceneRepository.findByProjectIdOrderByNameAsc(projectId);
    }

    @Transactional(readOnly = true)
    public Scene findSceneById(Long sceneId) {
        return sceneRepository.findById(sceneId)
                .orElseThrow(() -> new ResourceNotFoundException("场景", sceneId));
    }

    @Transactional
    public Scene saveScene(Scene scene) {
        return sceneRepository.save(scene);
    }

    @Transactional
    public void deleteScene(Long sceneId) {
        if (!sceneRepository.existsById(sceneId)) {
            throw new ResourceNotFoundException("场景", sceneId);
        }
        sceneRepository.deleteById(sceneId);
    }

    // ==================== AI Scene Extraction (ADR-4) ====================

    /**
     * AI 自动提取场景资产（异步执行）
     * 剧本完成后自动触发 LLM 提取，生成待确认记录
     */
    @Async("taskExecutor")
    public void extractScenesAsync(Long projectId, Long scriptId) {
        log.info("开始 AI 提取场景: projectId={}, scriptId={}", projectId, scriptId);

        if (projectId == null && scriptId != null) {
            projectId = scriptRepository.findById(scriptId)
                    .map(Script::getProjectId)
                    .orElseThrow(() -> new ResourceNotFoundException("剧本", scriptId));
        }

        try {
            String scriptContent = buildScriptContent(scriptId);
            if (scriptContent.isEmpty()) {
                sseService.pushNotification("scene-error", "没有可用的剧本内容");
                return;
            }

            sseService.pushNotification("scene-progress", "AI 正在提取场景信息...");

            String prompt = buildSceneExtractionPrompt(scriptContent);
            String result = modelCallService.callText(ModelConfig.ModelType.TEXT, prompt);

            List<ExtractedAsset> assets = parseExtractedScenes(result, projectId);
            for (ExtractedAsset asset : assets) {
                extractedAssetRepository.save(asset);
            }

            sseService.pushNotification("scene-completed",
                    "场景提取完成: " + assets.size() + " 个场景待确认");
            log.info("场景提取完成: {} 个场景", assets.size());

        } catch (ModelCallException e) {
            log.error("场景提取失败: {}", e.getMessage(), e);
            sseService.pushNotification("scene-error", "场景提取失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("场景提取异常: {}", e.getMessage(), e);
            sseService.pushNotification("scene-error", "场景提取异常: " + e.getMessage());
        }
    }

    // ==================== Quad View Generation (ADR-11) ====================

    /**
     * 生成场景四视图（异步执行）
     * 场景确认后自动生成正面/背面/左侧/右侧四视图
     */
    @Async("taskExecutor")
    public void generateQuadViewAsync(Long sceneId) {
        log.info("开始生成场景四视图: sceneId={}", sceneId);
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new ResourceNotFoundException("场景", sceneId));

        try {
            sseService.pushNotification("scene-progress",
                    String.format("正在生成场景 '%s' 的四视图...", scene.getName()));

            // Generate 4 views: front, back, left, right
            String[] views = {"front", "back", "left", "right"};
            String[] results = new String[4];

            for (int i = 0; i < views.length; i++) {
                sseService.pushNotification("scene-progress",
                        String.format("正在生成 '%s' 的%s视图 (%d/4)...",
                                scene.getName(), getViewLabel(views[i]), i + 1));

                String viewPrompt = buildQuadViewPrompt(scene, views[i]);
                results[i] = modelCallService.callImage(viewPrompt, null);
            }

            // Save quad view URLs
            scene.setFrontViewUrl(results[0]);
            scene.setBackViewUrl(results[1]);
            scene.setLeftViewUrl(results[2]);
            scene.setRightViewUrl(results[3]);
            sceneRepository.save(scene);

            sseService.pushNotification("scene-completed",
                    String.format("场景 '%s' 四视图生成完成", scene.getName()));
            log.info("场景四视图生成完成: sceneId={}", sceneId);

        } catch (ModelCallException e) {
            log.error("场景四视图生成失败: {}", e.getMessage(), e);
            sseService.pushNotification("scene-error",
                    String.format("场景 '%s' 四视图生成失败: %s", scene.getName(), e.getMessage()));
        }
    }

    /**
     * 重新生成单个视角
     */
    @Async("taskExecutor")
    public void regenerateSingleViewAsync(Long sceneId, String viewType) {
        log.info("开始重新生成单视角: sceneId={}, viewType={}", sceneId, viewType);
        Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new ResourceNotFoundException("场景", sceneId));

        try {
            sseService.pushNotification("scene-progress",
                    String.format("正在重新生成 '%s' 的%s视图...",
                            scene.getName(), getViewLabel(viewType)));

            String viewPrompt = buildQuadViewPrompt(scene, viewType);
            String result = modelCallService.callImage(viewPrompt, null);

            // Update the specific view URL
            switch (viewType.toLowerCase()) {
                case "front":
                    scene.setFrontViewUrl(result);
                    break;
                case "back":
                    scene.setBackViewUrl(result);
                    break;
                case "left":
                    scene.setLeftViewUrl(result);
                    break;
                case "right":
                    scene.setRightViewUrl(result);
                    break;
                default:
                    sseService.pushNotification("scene-error", "未知视角类型: " + viewType);
                    return;
            }
            sceneRepository.save(scene);

            sseService.pushNotification("scene-completed",
                    String.format("场景 '%s' 的%s视图重新生成完成", scene.getName(), getViewLabel(viewType)));
            log.info("单视角重新生成完成: sceneId={}, viewType={}", sceneId, viewType);

        } catch (ModelCallException e) {
            log.error("单视角重新生成失败: {}", e.getMessage(), e);
            sseService.pushNotification("scene-error",
                    String.format("重新生成失败: %s", e.getMessage()));
        }
    }

    // ==================== Extracted Scene Asset Management ====================

    @Transactional(readOnly = true)
    public List<ExtractedAsset> getExtractedSceneAssets(Long projectId) {
        if (projectId != null) {
            return extractedAssetRepository.findByProjectIdAndTypeAndStatus(
                    projectId, ExtractedAsset.ExtractedAssetType.SCENE,
                    ExtractedAsset.ExtractedStatus.PENDING);
        }
        return new ArrayList<>();
    }

    @Transactional
    public Scene confirmExtractedSceneAsset(Long assetId) {
        ExtractedAsset asset = extractedAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("待确认资产", assetId));

        if (asset.getStatus() == ExtractedAsset.ExtractedStatus.CONFIRMED) {
            throw new IllegalStateException("该资产已被确认");
        }

        Scene scene = new Scene();
        scene.setProjectId(asset.getProjectId());
        scene.setName(asset.getName());

        if (asset.getDescription() != null) {
            parseDescriptionToScene(asset.getDescription(), scene);
        }
        if (asset.getSuggestedImagePrompt() != null) {
            scene.setStyleHint(asset.getSuggestedImagePrompt());
        }

        asset.setStatus(ExtractedAsset.ExtractedStatus.CONFIRMED);
        extractedAssetRepository.save(asset);

        scene = sceneRepository.save(scene);

        asset.setConfirmedRefId(scene.getId());
        extractedAssetRepository.save(asset);

        return scene;
    }

    // ==================== Helper Methods ====================

    private String buildScriptContent(Long scriptId) {
        StringBuilder sb = new StringBuilder();
        scriptRepository.findById(scriptId).ifPresent(script -> {
            if (script.getOutline() != null) {
                sb.append("=== 大纲 ===\n").append(script.getOutline()).append("\n\n");
            }
            List<Episode> episodes = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId);
            for (Episode ep : episodes) {
                if (ep.getScriptContent() != null) {
                    sb.append("=== 第").append(ep.getEpisodeNumber())
                            .append("集: ").append(ep.getTitle()).append(" ===\n")
                            .append(ep.getScriptContent()).append("\n\n");
                }
            }
        });
        return sb.toString();
    }

    private String buildSceneExtractionPrompt(String scriptContent) {
        String truncated = scriptContent.length() > 30000
                ? scriptContent.substring(0, 30000) + "\n...(内容已截断)"
                : scriptContent;

        return "你是一名专业的漫剧场景分析师。请从以下剧本中提取所有场景信息。\n\n"
                + "每个场景输出以下 JSON 格式：\n"
                + "```json\n"
                + "{\"name\": \"场景名称\", \"description\": \"场景详细描述(地点、环境、氛围)\", "
                + "\"timeOfDay\": \"MORNING/NOON/AFTERNOON/EVENING/NIGHT/DAWN\", "
                + "\"weather\": \"SUNNY/CLOUDY/RAINY/SNOWY/FOGGY\", "
                + "\"styleHint\": \"场景风格关键词\"}\n"
                + "```\n\n"
                + "输出一个 JSON 数组包含所有场景。用 ```json 和 ``` 包裹。\n\n"
                + "=== 剧本内容 ===\n" + truncated + "\n\n"
                + "=== 请输出场景列表 ===";
    }

    private List<ExtractedAsset> parseExtractedScenes(String result, Long projectId) {
        List<ExtractedAsset> assets = new ArrayList<>();
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
                    for (JsonNode node : array) {
                        assets.add(createSceneAssetFromJson(node, projectId));
                    }
                    return assets;
                }
            }
        } catch (Exception e) {
            log.debug("JSON 解析失败，回退到原始文本: {}", e.getMessage());
        }

        // Fallback
        ExtractedAsset asset = new ExtractedAsset();
        asset.setProjectId(projectId);
        asset.setType(ExtractedAsset.ExtractedAssetType.SCENE);
        asset.setName("场景提取结果（需手动处理）");
        asset.setDescription(result);
        asset.setStatus(ExtractedAsset.ExtractedStatus.PENDING);
        assets.add(asset);

        return assets;
    }

    private ExtractedAsset createSceneAssetFromJson(JsonNode node, Long projectId) {
        ExtractedAsset asset = new ExtractedAsset();
        asset.setProjectId(projectId);
        asset.setType(ExtractedAsset.ExtractedAssetType.SCENE);
        asset.setName(node.has("name") ? node.get("name").asText() : "未知场景");
        asset.setStatus(ExtractedAsset.ExtractedStatus.PENDING);

        StringBuilder desc = new StringBuilder();
        if (node.has("description")) desc.append("描述: ").append(node.get("description").asText()).append("\n");
        if (node.has("timeOfDay")) desc.append("时段: ").append(node.get("timeOfDay").asText()).append("\n");
        if (node.has("weather")) desc.append("天气: ").append(node.get("weather").asText()).append("\n");
        if (node.has("styleHint")) desc.append("风格: ").append(node.get("styleHint").asText());
        asset.setDescription(desc.toString());

        if (node.has("styleHint")) {
            asset.setSuggestedImagePrompt("Comic scene, " + node.get("styleHint").asText()
                    + ", high quality, detailed background, cinematic lighting");
        }

        return asset;
    }

    private void parseDescriptionToScene(String description, Scene scene) {
        if (description == null) return;
        String[] lines = description.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("描述: ")) {
                scene.setDescription(line.substring(4).trim());
            } else if (line.startsWith("时段: ")) {
                try {
                    scene.setTimeOfDay(Scene.TimeOfDay.valueOf(line.substring(4).trim()));
                } catch (Exception ignored) {}
            } else if (line.startsWith("天气: ")) {
                try {
                    scene.setWeather(Scene.Weather.valueOf(line.substring(4).trim()));
                } catch (Exception ignored) {}
            } else if (line.startsWith("风格: ")) {
                scene.setStyleHint(line.substring(4).trim());
            }
        }
    }

    private String buildQuadViewPrompt(Scene scene, String viewType) {
        StringBuilder sb = new StringBuilder();
        sb.append("Comic manga scene background, ").append(scene.getName());
        if (scene.getStyleHint() != null && !scene.getStyleHint().isEmpty()) {
            sb.append(", ").append(scene.getStyleHint());
        }

        // Add view-specific direction
        switch (viewType.toLowerCase()) {
            case "front":
                sb.append(", front view, straight on angle");
                break;
            case "back":
                sb.append(", back view, from behind angle");
                break;
            case "left":
                sb.append(", left side view, from left angle");
                break;
            case "right":
                sb.append(", right side view, from right angle");
                break;
        }

        if (scene.getTimeOfDay() != null) {
            sb.append(", ").append(getTimeOfDayLabel(scene.getTimeOfDay()));
        }
        if (scene.getWeather() != null) {
            sb.append(", ").append(getWeatherLabel(scene.getWeather()));
        }

        sb.append(", high quality, detailed environment, cinematic lighting, game background art");
        return sb.toString();
    }

    private String getViewLabel(String viewType) {
        switch (viewType.toLowerCase()) {
            case "front": return "正面";
            case "back": return "背面";
            case "left": return "左侧";
            case "right": return "右侧";
            default: return viewType;
        }
    }

    private String getTimeOfDayLabel(Scene.TimeOfDay tod) {
        switch (tod) {
            case MORNING: return "morning";
            case NOON: return "noon";
            case AFTERNOON: return "afternoon";
            case EVENING: return "evening";
            case NIGHT: return "night";
            case DAWN: return "dawn";
            default: return "";
        }
    }

    private String getWeatherLabel(Scene.Weather w) {
        switch (w) {
            case SUNNY: return "sunny";
            case CLOUDY: return "cloudy";
            case RAINY: return "rainy";
            case SNOWY: return "snowy";
            case FOGGY: return "foggy";
            default: return "";
        }
    }
}
