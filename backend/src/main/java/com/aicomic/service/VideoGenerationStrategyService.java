package com.aicomic.service;

import com.aicomic.entity.Episode;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.StoryboardRepository;
import com.aicomic.service.model.ModelCallException;
import com.aicomic.service.model.ModelCallService;
import com.aicomic.service.model.VideoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 视频生成策略服务
 * 负责：整集视频生成、逐镜头回退策略（ADR-13）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoGenerationStrategyService {

    private final ModelCallService modelCallService;
    private final ReferenceResolutionService refService;
    private final StoryboardRepository storyboardRepository;

    /**
     * 尝试整集生成
     *
     * @param episode     剧集信息
     * @param storyboards 分镜列表
     * @return 是否成功
     */
    public boolean tryFullEpisodeGeneration(Episode episode, List<Storyboard> storyboards) {
        log.info("尝试整集生成：episodeId={}", episode.getId());

        try {
            Storyboard firstStoryboard = storyboards.get(0);
            String videoUrl = modelCallService.callVideo(
                    "Full episode animation for " + episode.getTitle(),
                    null,
                    null
            );

            if (videoUrl != null) {
                log.info("整集生成成功，应用到所有分镜");
                for (Storyboard sb : storyboards) {
                    sb.setGeneratedVideoUrl(videoUrl);
                    sb.setStatus(Storyboard.StoryboardStatus.VIDEO_DONE);
                    storyboardRepository.save(sb);
                }
                return true;
            }
        } catch (ModelCallException e) {
            log.warn("整集生成失败，准备回退到逐镜头模式：{}", e.getMessage());
        }

        return false;
    }

    /**
     * 逐镜头生成视频
     *
     * @param storyboards 分镜列表
     */
    public void generateAllShotsSequentially(List<Storyboard> storyboards) {
        log.info("开始逐镜头生成：共 {} 个分镜", storyboards.size());

        for (Storyboard storyboard : storyboards) {
            try {
                log.info("生成单镜头：storyboardId={}, sequence={}",
                        storyboard.getId(), storyboard.getSequence());

                VideoResponse response = null;
                String videoUrl = modelCallService.callVideo(
                        storyboard.getAction() != null ? storyboard.getAction() : "animation",
                        null,
                        null
                );

                if (videoUrl != null) {
                    storyboard.setGeneratedVideoUrl(videoUrl);
                    storyboard.setStatus(Storyboard.StoryboardStatus.VIDEO_DONE);
                    storyboardRepository.save(storyboard);
                    log.info("单镜头生成成功：storyboardId={}", storyboard.getId());
                } else {
                    storyboard.setStatus(Storyboard.StoryboardStatus.ERROR);
                    storyboardRepository.save(storyboard);
                    log.error("单镜头生成失败：storyboardId={}", storyboard.getId());
                }

            } catch (ModelCallException e) {
                log.error("单镜头生成异常：storyboardId={}, error={}",
                        storyboard.getId(), e.getMessage(), e);
                storyboard.setStatus(Storyboard.StoryboardStatus.ERROR);
                storyboardRepository.save(storyboard);
            }
        }
    }

    /**
     * 重新生成单个分镜视频
     *
     * @param storyboardId 分镜 ID
     */
    public void regenerateShotVideo(Long storyboardId) {
        Storyboard storyboard = storyboardRepository.findById(storyboardId)
                .orElseThrow(() -> new IllegalArgumentException("分镜不存在：" + storyboardId));

        try {
            log.info("重新生成单镜头：storyboardId={}", storyboardId);

            VideoResponse response = null;
            String videoUrl = modelCallService.callVideo(
                    storyboard.getAction() != null ? storyboard.getAction() : "animation",
                    null,
                    null
            );

            if (videoUrl != null) {
                storyboard.setGeneratedVideoUrl(videoUrl);
                storyboard.setStatus(Storyboard.StoryboardStatus.VIDEO_DONE);
                storyboardRepository.save(storyboard);
                log.info("单镜头重新生成成功：storyboardId={}", storyboardId);
            } else {
                storyboard.setStatus(Storyboard.StoryboardStatus.ERROR);
                storyboardRepository.save(storyboard);
                log.error("单镜头重新生成失败：storyboardId={}", storyboardId);
            }

        } catch (ModelCallException e) {
            log.error("单镜头重新生成异常：storyboardId={}, error={}",
                    storyboardId, e.getMessage(), e);
            storyboard.setStatus(Storyboard.StoryboardStatus.ERROR);
            storyboardRepository.save(storyboard);
        }
    }
}
