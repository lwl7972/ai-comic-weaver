package com.aicomic.service;

import com.aicomic.entity.Storyboard;
import com.aicomic.repository.StoryboardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 🎥 导演模块服务
 * 负责：整集视频生成调度、单镜头回退 + FFmpeg 拼接（ADR-13）、多模态引用构建
 * <p>
 * ⚠️ 当前状态：视频生成核心逻辑尚未实现，方法均为占位骨架。
 * 待后续迭代接入 Seedance 2.0 / SKYREELS-V4 等视频模型 API 后填充。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorService {

    private final StoryboardRepository storyboardRepository;

    /**
     * 生成整集视频（异步执行）
     * 优先一次生成整集，不支持时回退逐镜头 + FFmpeg 拼接
     */
    @Async("videoTaskExecutor")
    public void generateFullVideoAsync(Long projectId, Long scriptId) {
        log.info("开始生成整集视频: projectId={}, scriptId={}", projectId, scriptId);
        // TODO: 调度视频生成任务
        // 1. 获取所有分镜及其图片
        // 2. 尝试整集生成（调用视频模型）
        // 3. 失败则切换到逐镜头模式
        // 4. 逐镜头生成视频片段
        // 5. FFmpeg 拼接所有片段
        // 6. 通过 SSE 推送进度
        log.info("整集视频生成完成: projectId={}", projectId);
    }

    /**
     * 逐镜头生成视频片段（异步执行）
     */
    @Async("videoTaskExecutor")
    public void generateShotVideoAsync(Long storyboardId) {
        log.info("开始生成单镜头视频: storyboardId={}", storyboardId);
        // TODO: 单镜头视频生成
        // 1. 获取分镜信息及图片
        // 2. 构建多模态引用（角色图 + 场景图 + 首帧图）
        // 3. 组装视频提示词（动作+镜头语言+对白三层融合）
        // 4. 调用视频模型 API（Seedance 2.0 / SKYREELS-V4）
        // 5. 保存视频 URL
        // 6. 通过 SSE 推送进度
        log.info("单镜头视频生成完成: storyboardId={}", storyboardId);
    }

    /**
     * FFmpeg 拼接视频片段
     * @return 合成后视频 URL，片段列表为空时返回 null
     */
    public String concatVideoFragments(List<String> fragmentUrls) {
        if (fragmentUrls == null || fragmentUrls.isEmpty()) {
            log.warn("FFmpeg 拼接跳过: 视频片段列表为空");
            return null;
        }
        log.info("开始 FFmpeg 拼接: fragmentCount={}", fragmentUrls.size());
        // TODO: 调用 FFmpeg 拼接视频
        // 1. 下载所有视频片段
        // 2. 生成 concat 文件列表
        // 3. 执行 ffmpeg -f concat 拼接
        // 4. 返回合成后视频 URL
        log.info("FFmpeg 拼接完成");
        return null;
    }
}
