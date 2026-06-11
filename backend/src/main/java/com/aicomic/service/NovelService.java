package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.*;
import com.aicomic.repository.*;
import com.aicomic.service.model.ModelCallException;
import com.aicomic.service.model.ModelCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Novel import service
 * Novel upload/parse, chapter summary (ADR-9), novel-to-script conversion
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelService {

    private final NovelRepository novelRepository;
    private final ChapterSummaryRepository chapterSummaryRepository;
    private final ScriptRepository scriptRepository;
    private final EpisodeRepository episodeRepository;
    private final PromptTemplateService promptTemplateService;
    private final ModelCallService modelCallService;
    private final SseService sseService;

    @Value("${app.storage.path:./data/uploads}")
    private String storagePath;

    // ==================== Basic CRUD ====================

    @Transactional(readOnly = true)
    public List<Novel> getNovelsByProject(Long projectId) {
        return novelRepository.findByProjectIdOrderByImportedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<Novel> getNovel(Long id) {
        return novelRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ChapterSummary> getSummariesByNovel(Long novelId) {
        return chapterSummaryRepository.findByNovelIdOrderByChapterIndexAsc(novelId);
    }

    // ==================== Novel Upload & Parse ====================

    @Transactional
    public Novel uploadAndParse(Long projectId, MultipartFile file, String title) {
        try {
            // 1. Save file
            Path uploadDir = Paths.get(storagePath, "novels", String.valueOf(projectId));
            Files.createDirectories(uploadDir);
            // 安全处理文件名：仅取文件名部分，防止路径遍历攻击
            String originalName = file.getOriginalFilename();
            String safeFilename = (originalName != null)
                    ? new java.io.File(originalName).getName()
                    : "upload.txt";
            String filename = System.currentTimeMillis() + "_" + safeFilename;
            Path filePath = uploadDir.resolve(filename);
            file.transferTo(filePath.toFile());

            // 2. Create Novel entity
            Novel novel = new Novel();
            novel.setProjectId(projectId);
            novel.setTitle(title != null ? title : safeFilename);
            novel.setFilePath(filePath.toString());
            novel.setStatus(Novel.NovelStatus.IMPORTING);

            // 3. Parse chapters
            byte[] bytes = Files.readAllBytes(filePath);
            String content = new String(bytes, StandardCharsets.UTF_8);
            List<String> chapters = splitChapters(content);

            novel.setTotalChapters(chapters.size());
            novel.setStatus(Novel.NovelStatus.SUMMARIZING);
            novel = novelRepository.save(novel);

            // 4. Create chapter summary records
            for (int i = 0; i < chapters.size(); i++) {
                ChapterSummary summary = new ChapterSummary();
                summary.setNovelId(novel.getId());
                summary.setChapterIndex(i + 1);
                summary.setChapterTitle("Chapter " + (i + 1));
                summary.setStatus(ChapterSummary.SummaryStatus.PENDING);
                chapterSummaryRepository.save(summary);
            }

            log.info("Novel uploaded: id={}, chapters={}", novel.getId(), chapters.size());
            return novel;

        } catch (IOException e) {
            log.error("Novel upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("Novel upload failed: " + e.getMessage());
        }
    }

    // ==================== Chapter Summary (A.8.2) ====================

    @Async("taskExecutor")
    public void summarizeChaptersAsync(Long novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ResourceNotFoundException("Novel", novelId));

        novel.setStatus(Novel.NovelStatus.SUMMARIZING);
        novelRepository.save(novel);

        List<ChapterSummary> summaries = chapterSummaryRepository.findByNovelIdOrderByChapterIndexAsc(novelId);

        try {
            byte[] bytes = Files.readAllBytes(Paths.get(novel.getFilePath()));
            String content = new String(bytes, StandardCharsets.UTF_8);
            List<String> chapters = splitChapters(content);

            for (int i = 0; i < summaries.size() && i < chapters.size(); i++) {
                ChapterSummary summary = summaries.get(i);
                if (summary.getStatus() == ChapterSummary.SummaryStatus.COMPLETED) {
                    continue;
                }

                summary.setStatus(ChapterSummary.SummaryStatus.GENERATING);
                chapterSummaryRepository.save(summary);

                try {
                    String prompt = buildChapterSummaryPrompt(chapters.get(i), i + 1, chapters.size());
                    String result = modelCallService.callText(ModelConfig.ModelType.TEXT, prompt);

                    summary.setSummaryText(result);
                    summary.setStatus(ChapterSummary.SummaryStatus.COMPLETED);
                } catch (ModelCallException e) {
                    log.error("Chapter {} summary failed: {}", i + 1, e.getMessage());
                    summary.setStatus(ChapterSummary.SummaryStatus.ERROR);
                    summary.setSummaryText("Generation failed: " + e.getMessage());
                }
                chapterSummaryRepository.save(summary);

                sseService.pushNotification("novel-progress",
                        String.format("Summary progress: %d/%d", i + 1, summaries.size()));
            }

            novel.setStatus(Novel.NovelStatus.COMPLETED);
            novelRepository.save(novel);
            sseService.pushNotification("novel-completed", "All chapter summaries completed");

        } catch (Exception e) {
            log.error("Chapter summary error: {}", e.getMessage(), e);
            novel.setStatus(Novel.NovelStatus.ERROR);
            novel.setErrorMessage("Chapter summary failed: " + e.getMessage());
            novelRepository.save(novel);
            sseService.pushNotification("novel-error", "Chapter summary failed: " + e.getMessage());
        }
    }

    // ==================== Novel-to-Script Conversion (A.1) ====================

    @Async("taskExecutor")
    public void convertToScriptAsync(Long novelId) {
        Novel novel = novelRepository.findById(novelId)
                .orElseThrow(() -> new ResourceNotFoundException("Novel", novelId));

        novel.setStatus(Novel.NovelStatus.CONVERTING);
        novelRepository.save(novel);

        try {
            // 1. Collect completed chapter summaries
            List<ChapterSummary> summaries = chapterSummaryRepository.findByNovelIdOrderByChapterIndexAsc(novelId);
            String allSummaries = summaries.stream()
                    .filter(s -> s.getStatus() == ChapterSummary.SummaryStatus.COMPLETED)
                    .map(s -> "[" + s.getChapterTitle() + "]\n" + s.getSummaryText())
                    .collect(Collectors.joining("\n\n"));

            if (allSummaries.isEmpty()) {
                throw new ModelCallException("No completed chapter summaries. Please run chapter summary first.");
            }

            // 2. Generate outline (A.5) — 使用公共工具类
            sseService.pushNotification("novel-progress", "Generating script outline...");
            String outlinePrompt = ScriptPromptHelper.buildOutlinePrompt(allSummaries, novel.getTitle());
            String outlineText = modelCallService.callText(ModelConfig.ModelType.TEXT, outlinePrompt);

            // 3. Create Script
            Script script = new Script();
            script.setProjectId(novel.getProjectId());
            script.setTitle(novel.getTitle() + " - Script");
            script.setOutline(outlineText);
            script.setStatus(Script.ScriptStatus.IN_PROGRESS);
            script.setCurrentStep(Script.ScriptStep.OUTLINE);
            script = scriptRepository.save(script);

            // 4. Parse outline into episodes — 使用公共工具类
            sseService.pushNotification("novel-progress", "Parsing outline into episodes...");
            List<String> episodeOutlines = ScriptPromptHelper.parseOutlineToEpisodes(outlineText);

            for (int i = 0; i < episodeOutlines.size(); i++) {
                Episode episode = new Episode();
                episode.setScriptId(script.getId());
                episode.setEpisodeNumber(i + 1);
                episode.setTitle("Episode " + (i + 1));
                episode.setStatus(Episode.EpisodeStatus.DRAFT);
                episodeRepository.save(episode);
            }

            script.setTotalEpisodes(episodeOutlines.size());
            script.setCurrentStep(Script.ScriptStep.EPISODES);
            scriptRepository.save(script);

            // 5. Generate script for each episode — 使用公共工具类
            List<Episode> episodes = episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(script.getId());
            for (int i = 0; i < episodes.size(); i++) {
                Episode episode = episodes.get(i);
                String episodeOutline = i < episodeOutlines.size() ? episodeOutlines.get(i) : "";

                sseService.pushNotification("novel-progress",
                        String.format("Generating episode %d script (%d/%d)...", i + 1, i + 1, episodes.size()));

                String episodePrompt = ScriptPromptHelper.buildEpisodeScriptPromptFromSummary(
                        allSummaries, episodeOutline, i + 1);
                String scriptContent = modelCallService.callText(ModelConfig.ModelType.TEXT, episodePrompt);

                episode.setScriptContent(scriptContent);
                episode.setStatus(Episode.EpisodeStatus.READY);
                episodeRepository.save(episode);
            }

            // 6. Complete
            script.setStatus(Script.ScriptStatus.COMPLETED);
            script.setCurrentStep(Script.ScriptStep.REFINED);
            scriptRepository.save(script);

            novel.setStatus(Novel.NovelStatus.COMPLETED);
            novelRepository.save(novel);

            sseService.pushNotification("novel-completed",
                    "Novel-to-script conversion complete! " + episodes.size() + " episodes");

        } catch (Exception e) {
            log.error("Novel-to-script conversion error: {}", e.getMessage(), e);
            novel.setStatus(Novel.NovelStatus.ERROR);
            novel.setErrorMessage("Conversion failed: " + e.getMessage());
            novelRepository.save(novel);
            sseService.pushNotification("novel-error", "Novel-to-script failed: " + e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    private List<String> splitChapters(String content) {
        List<String> chapters = new ArrayList<>();
        Pattern pattern = Pattern.compile(
                "(第[零一二三四五六七八九十百千万\\d]+[章回节卷]|Chapter\\s*\\d+|CHAPTER\\s*\\d+)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        int lastEnd = 0;
        while (matcher.find()) {
            if (lastEnd > 0 || matcher.start() > 0) {
                String chapter = content.substring(lastEnd, matcher.start()).trim();
                if (!chapter.isEmpty()) {
                    chapters.add(chapter);
                }
            }
            lastEnd = matcher.start();
        }
        if (lastEnd < content.length()) {
            String chapter = content.substring(lastEnd).trim();
            if (!chapter.isEmpty()) {
                chapters.add(chapter);
            }
        }

        // If no chapter markers found, split by 5000 chars
        if (chapters.isEmpty()) {
            int chunkSize = 5000;
            for (int i = 0; i < content.length(); i += chunkSize) {
                String chunk = content.substring(i, Math.min(i + chunkSize, content.length())).trim();
                if (!chunk.isEmpty()) {
                    chapters.add(chunk);
                }
            }
        }

        return chapters;
    }

    private String buildChapterSummaryPrompt(String chapterContent, int chapterNum, int totalChapters) {
        return "You are a professional script analyst. Please provide a structured summary of the following novel chapter.\n\n"
                + "This is chapter " + chapterNum + " of " + totalChapters + " total chapters.\n\n"
                + "Please output the summary in the following format:\n"
                + "1. Summary (200-500 words)\n"
                + "2. Key characters (list character names and their actions)\n"
                + "3. Key scenes (list locations and scenes)\n"
                + "4. Plot turning points (if any)\n"
                + "5. Cliffhangers/foreshadowing (if any)\n"
                + "6. Continuity from previous chapter (if not chapter 1)\n\n"
                + "=== Novel Chapter Content ===\n" + chapterContent + "\n\n"
                + "=== Please output structured summary ===";
    }
}
