package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.common.event.AssetExtractedEvent;
import com.aicomic.entity.Character;
import com.aicomic.entity.ExtractedAsset;
import com.aicomic.entity.Project;
import com.aicomic.entity.Script;
import com.aicomic.entity.Episode;
import com.aicomic.entity.ModelConfig;
import com.aicomic.repository.CharacterRepository;
import com.aicomic.repository.ExtractedAssetRepository;
import com.aicomic.repository.ScriptRepository;
import com.aicomic.repository.EpisodeRepository;
import com.aicomic.service.model.ModelCallException;
import com.aicomic.service.model.ModelCallService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Character module service
 * AI asset extraction (ADR-4), 6-layer identity anchors (ADR-10), makeup image, character bible
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final ExtractedAssetRepository extractedAssetRepository;
    private final ScriptRepository scriptRepository;
    private final EpisodeRepository episodeRepository;
    private final ModelCallService modelCallService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;
    private final PipelineStateService pipelineStateService;

    // ==================== Basic CRUD ====================

    @Transactional(readOnly = true)
    public List<Character> getCharactersByProject(Long projectId) {
        return characterRepository.findByProjectIdOrderByNameAsc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<Character> getCharacter(Long id) {
        return characterRepository.findById(id);
    }

    @Transactional
    public Character saveCharacter(Character character) {
        Character saved = characterRepository.save(character);
        if (saved.getProjectId() != null) {
            pipelineStateService.markDirty(saved.getProjectId(), Project.PipelineStage.CHARACTER);
        }
        return saved;
    }

    @Transactional
    public void deleteCharacter(Long characterId) {
        if (!characterRepository.existsById(characterId)) {
            throw new ResourceNotFoundException("Character", characterId);
        }
        Character character = characterRepository.findById(characterId).orElse(null);
        Long projectId = character != null ? character.getProjectId() : null;
        characterRepository.deleteById(characterId);
        if (projectId != null) {
            pipelineStateService.markDirty(projectId, Project.PipelineStage.CHARACTER);
        }
    }

    // ==================== AI Asset Extraction (ADR-4) ====================

    /**
     * 监听资产提取事件，自动触发角色提取
     */
    @Async("taskExecutor")
    @EventListener(condition = "#event.assetType == 'CHARACTER'")
    public void onAssetExtractedEvent(AssetExtractedEvent event) {
        log.info("接收到角色提取事件：projectId={}, scriptId={}", event.getProjectId(), event.getScriptId());
        extractCharactersAsync(event.getProjectId(), event.getScriptId());
    }

    @Async("taskExecutor")
    public void extractCharactersAsync(Long projectId, Long scriptId) {
        log.info("Start AI character extraction: projectId={}, scriptId={}", projectId, scriptId);

        if (projectId == null && scriptId != null) {
            projectId = scriptRepository.findById(scriptId)
                    .map(Script::getProjectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Script", scriptId));
        }

        try {
            String scriptContent = buildScriptContent(scriptId);
            if (scriptContent.isEmpty()) {
                sseService.pushNotification("character-error", "No script content available");
                return;
            }

            sseService.pushNotification("character-progress", "Extracting characters with AI...");

            String prompt = buildCharacterExtractionPromptWithTemplate(scriptContent);
            String result = modelCallService.callText(ModelConfig.ModelType.TEXT, prompt);

            List<ExtractedAsset> assets = parseExtractedCharacters(result, projectId);
            for (ExtractedAsset asset : assets) {
                extractedAssetRepository.save(asset);
            }

            pipelineStateService.markDirty(projectId, Project.PipelineStage.CHARACTER);

            sseService.pushNotification("character-completed",
                    "Character extraction complete: " + assets.size() + " characters pending confirmation");
            log.info("Character extraction complete: {} characters found", assets.size());

        } catch (Exception e) {
            log.error("Character extraction failed: {}", e.getMessage(), e);
            sseService.pushNotification("character-error", "Character extraction failed: " + e.getMessage());
        }
    }

    // ==================== Makeup Image Generation (ADR-10) ====================

    /**
     * 生成角色定妆图（异步执行）
     * ADR-10: 参考图 + Prompt 双重一致性保障
     */
    @Async("taskExecutor")
    public void generateMakeupImageAsync(Long characterId) {
        log.info("开始生成定妆图：characterId={}", characterId);
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResourceNotFoundException("Character", characterId));

        try {
            sseService.pushNotification("character-progress",
                    String.format("正在生成 '%s' 的定妆图...", character.getName()));

            // ADR-10: 构建包含 6 层锚点的完整提示词
            String makeupPrompt = buildMakeupPromptWithTemplate(character);
            
            // 如果有参考图，使用图生图模式
            String referenceImageUrl = character.getReferenceImageUrl();
            
            String imageUrl = modelCallService.callImage(makeupPrompt, referenceImageUrl);

            character.setMakeupImageUrl(imageUrl);
            character.setAnchorPrompt(makeupPrompt);
            characterRepository.save(character);

            pipelineStateService.markDirty(character.getProjectId(), Project.PipelineStage.CHARACTER);

            sseService.pushNotification("character-completed",
                    String.format("'%s' 的定妆图已生成", character.getName()));
            log.info("定妆图生成完成：characterId={}, imageUrl={}", characterId, imageUrl);

        } catch (ModelCallException e) {
            log.error("定妆图生成失败：{}", e.getMessage(), e);
            sseService.pushNotification("character-error",
                    String.format("'%s' 定妆图生成失败：%s", character.getName(), e.getMessage()));
        } catch (Exception e) {
            log.error("定妆图生成异常：{}", e.getMessage(), e);
            sseService.pushNotification("character-error",
                    String.format("'%s' 定妆图生成异常：%s", character.getName(), e.getMessage()));
        }
    }

    // ==================== Extracted Asset Management ====================

    @Transactional(readOnly = true)
    public List<ExtractedAsset> getExtractedAssets(Long projectId, String type) {
        if (projectId != null) {
            ExtractedAsset.ExtractedAssetType assetType = ExtractedAsset.ExtractedAssetType.valueOf(type);
            return extractedAssetRepository.findByProjectIdAndTypeAndStatus(
                    projectId, assetType, ExtractedAsset.ExtractedStatus.PENDING);
        }
        return new ArrayList<>();
    }

    @Transactional
    public Character confirmExtractedAsset(Long assetId) {
        ExtractedAsset asset = extractedAssetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("ExtractedAsset", assetId));

        if (asset.getStatus() == ExtractedAsset.ExtractedStatus.CONFIRMED) {
            throw new IllegalStateException("Asset already confirmed");
        }

        Character character = new Character();
        character.setProjectId(asset.getProjectId());
        character.setName(asset.getName());
        character.setRole(Character.CharacterRole.SUPPORTING);
        character.setGender(Character.Gender.OTHER);

        if (asset.getDescription() != null) {
            parseDescriptionToCharacter(asset.getDescription(), character);
        }

        if (asset.getSuggestedImagePrompt() != null) {
            character.setAnchorPrompt(asset.getSuggestedImagePrompt());
        }

        character.setExtractedFromScript(true);

        asset.setStatus(ExtractedAsset.ExtractedStatus.CONFIRMED);
        extractedAssetRepository.save(asset);

        character = characterRepository.save(character);

        asset.setConfirmedRefId(character.getId());
        extractedAssetRepository.save(asset);

        pipelineStateService.markDirty(character.getProjectId(), Project.PipelineStage.CHARACTER);

        return character;
    }

    // ==================== Helper Methods ====================

    private String buildScriptContent(Long scriptId) {
        StringBuilder sb = new StringBuilder();
        scriptRepository.findById(scriptId).ifPresent(script -> {
            if (script.getOutline() != null) {
                sb.append("=== Outline ===\n").append(script.getOutline()).append("\n\n");
            }
            List<Episode> episodes = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId);
            for (Episode ep : episodes) {
                if (ep.getScriptContent() != null) {
                    sb.append("=== Episode ").append(ep.getEpisodeNumber())
                            .append(": ").append(ep.getTitle()).append(" ===\n")
                            .append(ep.getScriptContent()).append("\n\n");
                }
            }
        });
        return sb.toString();
    }

    private String buildCharacterExtractionPrompt(String scriptContent) {
        String truncated = scriptContent.length() > 30000
                ? scriptContent.substring(0, 30000) + "\n...(content truncated)"
                : scriptContent;

        return "You are a professional comic drama character analyst. Extract all character information from the following script.\n\n"
                + "For each character, output the following JSON format:\n"
                + "```json\n"
                + "{\"name\": \"character name\", \"role\": \"PROTAGONIST/ANTAGONIST/SUPPORTING/EXTRA\", "
                + "\"gender\": \"MALE/FEMALE/OTHER\", \"ageRange\": \"e.g. 20-30\", "
                + "\"appearance\": \"detailed appearance description\", \"personality\": \"personality description\"}\n"
                + "```\n\n"
                + "Output a JSON array containing all characters. Wrap with ```json and ```.\n\n"
                + "=== Script Content ===\n" + truncated + "\n\n"
                + "=== Please output character list ===";
    }

    private List<ExtractedAsset> parseExtractedCharacters(String result, Long projectId) {
        List<ExtractedAsset> assets = new ArrayList<>();
        try {
            String jsonStr = extractJsonFromResult(result);
            
            if (jsonStr.startsWith("[")) {
                JsonNode array = objectMapper.readTree(jsonStr);
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        assets.add(createAssetFromJson(node, projectId));
                    }
                    log.info("成功解析 {} 个角色", assets.size());
                    return assets;
                }
            }
        } catch (Exception e) {
            log.warn("JSON 解析失败，降级为原始文本记录：{}", e.getMessage());
        }

        // Fallback: create raw record
        ExtractedAsset asset = new ExtractedAsset();
        asset.setProjectId(projectId);
        asset.setType(ExtractedAsset.ExtractedAssetType.CHARACTER);
        asset.setName("提取结果（需要手动处理）");
        asset.setDescription(result);
        asset.setStatus(ExtractedAsset.ExtractedStatus.PENDING);
        asset.setExtraData("{\"parseError\": \"JSON 解析失败，请手动编辑\"}");
        assets.add(asset);

        return assets;
    }

    /**
     * 从 LLM 返回结果中提取 JSON 字符串
     */
    private String extractJsonFromResult(String result) {
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
            if (endIdx > startIdx) {
                jsonStr = result.substring(startIdx, endIdx);
            }
        }
        return jsonStr;
    }

    private ExtractedAsset createAssetFromJson(JsonNode node, Long projectId) {
        ExtractedAsset asset = new ExtractedAsset();
        asset.setProjectId(projectId);
        asset.setType(ExtractedAsset.ExtractedAssetType.CHARACTER);
        asset.setName(node.has("name") ? node.get("name").asText() : "Unknown");
        asset.setStatus(ExtractedAsset.ExtractedStatus.PENDING);

        StringBuilder desc = new StringBuilder();
        if (node.has("role")) desc.append("Role: ").append(node.get("role").asText()).append("\n");
        if (node.has("gender")) desc.append("Gender: ").append(node.get("gender").asText()).append("\n");
        if (node.has("ageRange")) desc.append("Age: ").append(node.get("ageRange").asText()).append("\n");
        if (node.has("appearance")) desc.append("Appearance: ").append(node.get("appearance").asText()).append("\n");
        if (node.has("personality")) desc.append("Personality: ").append(node.get("personality").asText()).append("\n");
        asset.setDescription(desc.toString());

        if (node.has("appearance")) {
            asset.setSuggestedImagePrompt("Comic character makeup, " + node.get("appearance").asText()
                    + ", high quality, fine details");
        }

        return asset;
    }

    private void parseDescriptionToCharacter(String description, Character character) {
        if (description == null) return;
        String[] lines = description.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Role: ")) {
                String val = line.substring(6).trim();
                try {
                    character.setRole(Character.CharacterRole.valueOf(val.toUpperCase()));
                } catch (Exception ignored) {}
            } else if (line.startsWith("Gender: ")) {
                String val = line.substring(8).trim();
                try {
                    character.setGender(Character.Gender.valueOf(val.toUpperCase()));
                } catch (Exception ignored) {}
            } else if (line.startsWith("Age: ")) {
                character.setAgeRange(line.substring(5).trim());
            } else if (line.startsWith("Appearance: ")) {
                character.setAppearance(line.substring(12).trim());
            } else if (line.startsWith("Personality: ")) {
                character.setPersonality(line.substring(13).trim());
            }
        }
    }

    private String buildMakeupPrompt(Character character) {
        StringBuilder sb = new StringBuilder();
        sb.append("Comic character makeup image, ");
        sb.append(character.getName()).append(", ");
        if (character.getGender() != null) {
            sb.append(character.getGender() == Character.Gender.MALE ? "male, " : "female, ");
        }
        if (character.getAgeRange() != null) {
            sb.append(character.getAgeRange()).append(", ");
        }
        if (character.getAppearance() != null) {
            sb.append(character.getAppearance()).append(", ");
        }
        if (character.getPersonality() != null) {
            sb.append(character.getPersonality()).append(", ");
        }
        sb.append("high quality, fine details, comic style");
        return sb.toString();
    }

    // ==================== Template-based Prompt Generation ====================

    /**
     * 构建角色提取提示词（使用模板）
     */
    private String buildCharacterExtractionPromptWithTemplate(String scriptContent) {
        try {
            var templateOpt = promptTemplateService.getTemplateByName(
                PromptTemplate.TemplateCategory.CHARACTER, "角色提取");
            if (templateOpt.isPresent()) {
                var template = templateOpt.get();
                java.util.Map<String, String> variables = new java.util.HashMap<>();
                variables.put("scriptContent", scriptContent.length() > 30000 ? 
                    scriptContent.substring(0, 30000) + "
...(content truncated)" : scriptContent);
                return promptTemplateService.renderTemplate(template.getId(), variables);
            }
        } catch (Exception e) {
            log.warn("模板渲染失败，使用硬编码提示词：{}", e.getMessage());
        }
        return buildCharacterExtractionPromptWithTemplate(scriptContent);
    }

    /**
     * 构建角色定妆图提示词（使用模板）
     */
    private String buildMakeupPromptWithTemplate(Character character) {
        try {
            var templateOpt = promptTemplateService.getTemplateByName(
                PromptTemplate.TemplateCategory.CHARACTER, "角色定妆图提示词");
            if (templateOpt.isPresent()) {
                var template = templateOpt.get();
                java.util.Map<String, String> variables = new java.util.HashMap<>();
                variables.put("name", character.getName());
                variables.put("gender", character.getGender() == Character.Gender.MALE ? "male" : "female");
                variables.put("ageRange", character.getAgeRange());
                variables.put("appearance", character.getAppearance());
                variables.put("personality", character.getPersonality());
                variables.put("occupation", character.getOccupation());
                return promptTemplateService.renderTemplate(template.getId(), variables);
            }
        } catch (Exception e) {
            log.warn("模板渲染失败，使用硬编码提示词：{}", e.getMessage());
        }
        return buildMakeupPromptWithTemplate(character);
    }

}
