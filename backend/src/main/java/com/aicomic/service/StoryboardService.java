package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.StoryboardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 获取剧集的所有分镜
     */
    public List<Storyboard> getStoryboardsByEpisode(Long episodeId) {
        return storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
    }

    /**
     * 创建或更新分镜
     */
    @Transactional
    public Storyboard saveStoryboard(Storyboard storyboard) {
        return storyboardRepository.save(storyboard);
    }

    /**
     * 批量保存分镜
     */
    @Transactional
    public List<Storyboard> saveStoryboards(List<Storyboard> storyboards) {
        return storyboardRepository.saveAll(storyboards);
    }

    /**
     * 删除分镜
     */
    @Transactional
    public void deleteStoryboard(Long storyboardId) {
        if (!storyboardRepository.existsById(storyboardId)) {
            throw new ResourceNotFoundException("分镜", storyboardId);
        }
        storyboardRepository.deleteById(storyboardId);
    }

    /**
     * AI 解析剧本为分镜数据（异步执行）
     * 三步流程第一步：解析
     */
    @Async("taskExecutor")
    public void parseScriptToStoryboardAsync(Long scriptId) {
        log.info("开始 AI 解析剧本为分镜: scriptId={}", scriptId);
        // TODO: 调用 LLM 解析剧本
        // 1. 获取剧本全文及剧集
        // 2. 组装解析提示词（PromptTemplate SCRIPT/A.8.1 类）
        // 3. 调用 LLM API 解析为结构化分镜数据
        // 4. 批量创建 Storyboard 实体
        // 5. 通过 SSE 推送进度
        log.info("剧本解析为分镜完成: scriptId={}", scriptId);
    }

    /**
     * 批量生成分镜图（异步执行）
     * 三步流程第三步：生成图片
     */
    @Async("taskExecutor")
    public void generateStoryboardImagesAsync(Long episodeId) {
        log.info("开始批量生成分镜图: episodeId={}", episodeId);
        // TODO: 批量调用图像模型生成分镜图
        // 1. 获取剧集所有分镜
        // 2. 逐个组装分镜提示词
        // 3. 调用图像模型 API
        // 4. 保存分镜图 URL
        // 5. 通过 SSE 推送进度
        log.info("分镜图批量生成完成: episodeId={}", episodeId);
    }
}
