package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.*;
import com.aicomic.repository.*;
import com.aicomic.service.model.ModelCallException;
import com.aicomic.service.model.ModelCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Script module service
 * Novel import, AI outline generation, per-episode script writing, chapter summary (ADR-9)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final EpisodeRepository episodeRepository;
    private final NovelRepository novelRepository;
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final PromptTemplateService promptTemplateService;
    private final ModelCallService modelCallService;
    private final SseService sseService;
    private final PipelineStateService pipelineStateService;

    // ==================== Basic CRUD ====================

    @Transactional(readOnly = true)
    public List<Script> getScriptsByProject(Long projectId) {
        return scriptRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<Script> getScript(Long id) {
        return scriptRepository.findById(id);
    }

    @Transactional
    public Script saveScript(Script script) {
        Script saved = scriptRepository.save(script);
        pipelineStateService.markDirty(saved.getProjectId(), Project.PipelineStage.SCRIPT);
        return saved;
    }

    @Transactional
    public void deleteScript(Long id) {
        if (!scriptRepository.existsById(id)) {
            throw new ResourceNotFoundException("Script", id);
        }
        Script script = scriptRepository.findById(id).orElse(null);
        Long projectId = script != null ? script.getProjectId() : null;
        List<Episode> episodes = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(id);
        episodeRepository.deleteAll(episodes);
        scriptRepository.deleteById(id);
        if (projectId != null) {
            pipelineStateService.markDirty(projectId, Project.PipelineStage.SCRIPT);
        }
    }

    @Transactional(readOnly = true)
    public List<Episode> getEpisodesByScript(Long scriptId) {
        return episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId);
    }

    @Transactional(readOnly = true)
    public Optional<Episode> getEpisode(Long id) {
        return episodeRepository.findById(id);
    }

    @Transactional
    public Episode saveEpisode(Episode episode) {
        Episode saved = episodeRepository.save(episode);
        if (saved.getScriptId() != null) {
            scriptRepository.findById(saved.getScriptId()).ifPresent(s ->
                    pipelineStateService.markDirty(s.getProjectId(), Project.PipelineStage.SCRIPT));
        }
        return saved;
    }

    // ==================== AI Outline Generation ====================

    @Async("taskExecutor")
    public void generateOutlineAsync(Long projectId, Long scriptId) {
        log.info("Start generating outline: projectId={}, scriptId={}", projectId, scriptId);
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new ResourceNotFoundException("Script", scriptId));

        try {
            script.setStatus(Script.ScriptStatus.IN_PROGRESS);
            scriptRepository.save(script);
            sseService.pushNotification("script-progress", "Generating outline...");

            String summaryText = buildSummaryContext(projectId);
            String prompt = buildOutlinePromptWithTemplate(summaryText, script.getTitle(), true);
            String outlineText = modelCallService.callText(ModelConfig.ModelType.TEXT, prompt);

            List<String> episodeOutlines = ScriptPromptHelper.parseOutlineToEpisodes(outlineText);

            script.setOutline(outlineText);
            script.setTotalEpisodes(episodeOutlines.size());
            script.setCurrentStep(Script.ScriptStep.EPISODES);

            for (int i = 0; i < episodeOutlines.size(); i++) {
                Episode episode = new Episode();
                episode.setScriptId(scriptId);
                episode.setEpisodeNumber(i + 1);
                episode.setTitle("Episode " + (i + 1));
                episode.setStatus(Episode.EpisodeStatus.DRAFT);
                episodeRepository.save(episode);
            }

            scriptRepository.save(script);
            pipelineStateService.markDirty(projectId, Project.PipelineStage.SCRIPT);
            sseService.pushNotification("script-completed",
                    "Outline generated: " + episodeOutlines.size() + " episodes");
            log.info("Outline generated: scriptId={}, episodes={}", scriptId, episodeOutlines.size());

        } catch (Exception e) {
            log.error("Outline generation failed: {}", e.getMessage(), e);
            script.setStatus(Script.ScriptStatus.ERROR);
            scriptRepository.save(script);
            sseService.pushNotification("script-error", "Outline generation failed: " + e.getMessage());
        }
    }

    // ==================== AI Episode Script Generation ====================

    @Async("taskExecutor")
    public void generateEpisodeScriptAsync(Long scriptId, Long episodeId) {
        log.info("Start generating episode script: scriptId={}, episodeId={}", scriptId, episodeId);
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode", episodeId));

        try {
            Script script = scriptRepository.findById(scriptId)
                    .orElseThrow(() -> new ResourceNotFoundException("Script", scriptId));

            sseService.pushNotification("script-progress",
                    String.format("Generating episode %d script...", episode.getEpisodeNumber()));

            String outline = script.getOutline() != null ? script.getOutline() : "";
            String previousEpisodes = buildPreviousEpisodesContext(scriptId, episode.getEpisodeNumber());

            String prompt = buildEpisodeScriptPromptWithTemplate(
                    outline, previousEpisodes, episode.getEpisodeNumber(), episode.getTitle());

            String scriptContent = modelCallService.callText(ModelConfig.ModelType.TEXT, prompt);

            episode.setScriptContent(scriptContent);
            episode.setStatus(Episode.EpisodeStatus.READY);
            episodeRepository.save(episode);

            pipelineStateService.markDirty(script.getProjectId(), Project.PipelineStage.SCRIPT);

            sseService.pushNotification("script-progress",
                    String.format("Episode %d script generated", episode.getEpisodeNumber()));
            log.info("Episode script generated: episodeId={}", episodeId);

        } catch (Exception e) {
            log.error("Episode script generation failed: {}", e.getMessage(), e);
            episode.setStatus(Episode.EpisodeStatus.ERROR);
            episodeRepository.save(episode);
            sseService.pushNotification("script-error",
                    String.format("Episode %d script failed: %s", episode.getEpisodeNumber(), e.getMessage()));
        }
    }

    // ==================== Template-based Prompt Generation ====================

    /**
     * 构建大纲生成提示词（使用模板）优先使用模板系统，回退到硬编码 helper
     */
    private String buildOutlinePromptWithTemplate(String summaryText, String title, boolean isNovelSummary) {
        try {
            var templateOpt = promptTemplateService.getTemplateByName(
                PromptTemplate.TemplateCategory.SCRIPT, isNovelSummary ? "小说转大纲" : "大纲生成");
            if (templateOpt.isPresent()) {
                var template = templateOpt.get();
                java.util.Map<String, String> variables = new java.util.HashMap<>();
                if (isNovelSummary) {
                    variables.put("totalEpisodes", "8");
                    variables.put("novelSummary", summaryText);
                } else {
                    variables.put("totalEpisodes", "8");
                    variables.put("storySummary", summaryText);
                    variables.put("genre", "都市/情感");
                }
                return promptTemplateService.renderTemplate(template.getId(), variables);
            }
        } catch (Exception e) {
            log.warn("模板渲染失败，使用硬编码提示词：{}", e.getMessage());
        }
        return ScriptPromptHelper.buildOutlinePrompt(summaryText, title);
    }

    /**
     * 构建剧本生成提示词（使用模板）优先使用模板系统，回退到硬编码 helper
     */
    private String buildEpisodeScriptPromptWithTemplate(String outline, String previousEpisodes,
                                                         int episodeNum, String episodeTitle) {
        try {
            var templateOpt = promptTemplateService.getTemplateByName(
                PromptTemplate.TemplateCategory.SCRIPT, "剧本生成");
            if (templateOpt.isPresent()) {
                var template = templateOpt.get();
                java.util.Map<String, String> variables = new java.util.HashMap<>();
                variables.put("episodeNumber", String.valueOf(episodeNum));
                variables.put("duration", "3");
                variables.put("episodeOutline", outline);
                variables.put("previousEpisode", previousEpisodes);
                variables.put("characterList", "暂无角色信息");
                return promptTemplateService.renderTemplate(template.getId(), variables);
            }
        } catch (Exception e) {
            log.warn("模板渲染失败，使用硬编码提示词：{}", e.getMessage());
        }
        return ScriptPromptHelper.buildEpisodeScriptPrompt(outline, previousEpisodes, episodeNum, episodeTitle);
    }

    // ==================== Helper Methods ====================

    private String buildSummaryContext(Long projectId) {
        StringBuilder sb = new StringBuilder();
        novelRepository.findTopByProjectIdOrderByImportedAtDesc(projectId).ifPresent(novel -> {
            List<ChapterSummary> summaries = chapterSummaryRepository
                    .findByNovelIdOrderByChapterIndexAsc(novel.getId());
            for (ChapterSummary s : summaries) {
                if (s.getStatus() == ChapterSummary.SummaryStatus.COMPLETED && s.getSummaryText() != null) {
                    sb.append("[").append(s.getChapterTitle()).append("]\n")
                            .append(s.getSummaryText()).append("\n\n");
                }
            }
        });
        return sb.toString();
    }

    private String buildPreviousEpisodesContext(Long scriptId, int currentEpisodeNum) {
        List<Episode> allEpisodes = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId);
        StringBuilder sb = new StringBuilder();
        for (Episode ep : allEpisodes) {
            if (ep.getEpisodeNumber() < currentEpisodeNum && ep.getScriptContent() != null) {
                sb.append("=== Episode ").append(ep.getEpisodeNumber()).append(": ")
                        .append(ep.getTitle()).append(" ===\n");
                String content = ep.getScriptContent();
                if (content.length() > 2000) {
                    content = content.substring(0, 2000) + "...(truncated)";
                }
                sb.append(content).append("\n\n");
            }
        }
        return sb.toString();
    }
}
