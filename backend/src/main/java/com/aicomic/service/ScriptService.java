package com.aicomic.service;

import com.aicomic.entity.*;
import com.aicomic.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 📝 剧本模块服务
 * 负责：小说导入解析、AI 大纲生成、逐集剧本创作、分章节摘要（ADR-9）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final EpisodeRepository episodeRepository;
    private final NovelRepository novelRepository;
    private final ChapterSummaryRepository chapterSummaryRepository;

    /**
     * 获取项目的所有剧本
     */
    @Transactional(readOnly = true)
    public List<Script> getScriptsByProject(Long projectId) {
        return scriptRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * 创建或更新剧本
     */
    @Transactional
    public Script saveScript(Script script) {
        return scriptRepository.save(script);
    }

    /**
     * 获取剧本的所有剧集
     */
    @Transactional(readOnly = true)
    public List<Episode> getEpisodesByScript(Long scriptId) {
        return episodeRepository.findByScriptIdOrderByEpisodeNumberAsc(scriptId);
    }

    /**
     * 创建或更新剧集
     */
    @Transactional
    public Episode saveEpisode(Episode episode) {
        return episodeRepository.save(episode);
    }

    /**
     * AI 生成剧本大纲（异步执行）
     * 调用 LLM 基于小说/章节摘要生成完整剧集大纲
     */
    @Async("taskExecutor")
    public void generateOutlineAsync(Long projectId, Long scriptId) {
        log.info("开始生成剧本大纲: projectId={}, scriptId={}", projectId, scriptId);
        // TODO: 调用 LLM 生成大纲
        // 1. 获取章节摘要列表
        // 2. 组装提示词（PromptTemplate SCRIPT 类）
        // 3. 调用 LLM API 生成大纲
        // 4. 解析大纲为 episodes 列表
        // 5. 批量保存剧集
        // 6. 通过 SSE 推送进度
        log.info("剧本大纲生成完成: scriptId={}", scriptId);
    }

    /**
     * AI 生成逐集剧本（异步执行）
     */
    @Async("taskExecutor")
    public void generateEpisodeScriptAsync(Long scriptId, Long episodeId) {
        log.info("开始生成剧集剧本: scriptId={}, episodeId={}", scriptId, episodeId);
        // TODO: 调用 LLM 生成逐集剧本
        log.info("剧集剧本生成完成: episodeId={}", episodeId);
    }
}
