package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.Scene;
import com.aicomic.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 🌄 场景模块服务
 * 负责：场景创建管理、四视图生成（ADR-11）、场景风格一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SceneService {

    private final SceneRepository sceneRepository;

    /**
     * 获取项目的所有场景
     */
    public List<Scene> getScenesByProject(Long projectId) {
        return sceneRepository.findByProjectIdOrderByNameAsc(projectId);
    }

    /**
     * 创建或更新场景
     */
    @Transactional
    public Scene saveScene(Scene scene) {
        return sceneRepository.save(scene);
    }

    /**
     * 删除场景
     */
    @Transactional
    public void deleteScene(Long sceneId) {
        if (!sceneRepository.existsById(sceneId)) {
            throw new ResourceNotFoundException("场景", sceneId);
        }
        sceneRepository.deleteById(sceneId);
    }

    /**
     * AI 自动提取场景资产（异步执行）
     */
    @Async("taskExecutor")
    public void extractScenesAsync(Long projectId, Long scriptId) {
        log.info("开始 AI 提取场景: projectId={}, scriptId={}", projectId, scriptId);
        // TODO: 调用 LLM 提取场景
        // 1. 获取剧本全文
        // 2. 组装资产提取提示词
        // 3. 调用 LLM API 提取场景列表
        // 4. 生成 ExtractedAsset 待确认记录
        // 5. 通过 SSE 推送进度
        log.info("场景提取完成: projectId={}", projectId);
    }

    /**
     * 生成场景四视图（异步执行）
     * 场景确认后自动生成正面/背面/左侧/右侧四视图
     */
    @Async("taskExecutor")
    public void generateQuadViewAsync(Long sceneId) {
        log.info("开始生成场景四视图: sceneId={}", sceneId);
        // TODO: 调用图像模型生成四视图
        // 1. 获取场景信息
        // 2. 组装四视图提示词（PromptTemplate SCENE/A.7 类）
        // 3. 调用图像模型 API 生成四视图
        // 4. 保存四视图 URL
        // 5. 通过 SSE 推送进度
        log.info("场景四视图生成完成: sceneId={}", sceneId);
    }
}
