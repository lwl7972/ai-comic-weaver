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
        return scriptRepository.save(script);
    }

    @Transactional
    public void deleteScript(Long id) {
        if (!scriptRepository.existsById(id)) {
            throw new ResourceNotFoundException("Script", id);
        }
        List<Episode> episodes = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(id);
        episodeRepository.deleteAll(episodes);
        scriptRepository.deleteById(id);
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
        return episodeRepository.save(episode);
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
            String prompt = ScriptPromptHelper.buildOutlinePrompt(summaryText, script.getTitle());
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

            String prompt = ScriptPromptHelper.buildEpisodeScriptPrompt(
                    outline, previousEpisodes, episode.getEpisodeNumber(), episode.getTitle());

            String scriptContent = modelCallService.callText(ModelConfig.ModelType.TEXT, prompt);

            episode.setScriptContent(scriptContent);
            episode.setStatus(Episode.EpisodeStatus.READY);
            episodeRepository.save(episode);

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
