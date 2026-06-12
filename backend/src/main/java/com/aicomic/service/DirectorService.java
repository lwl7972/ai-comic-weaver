package com.aicomic.service;

import com.aicomic.common.util.FFmpegUtils;
import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.Character;
import com.aicomic.entity.Episode;
import com.aicomic.entity.Project;
import com.aicomic.entity.Scene;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.StoryboardRepository;
import com.aicomic.repository.EpisodeRepository;
import com.aicomic.service.model.ModelCallException;
import com.aicomic.service.model.ModelCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 🎥 导演模块服务
 * 负责：整集视频生成调度、单镜头回退 + FFmpeg 拼接（ADR-13）、多模态引用构建
 * <p>
 * 视频模型通过 ModelCallService.callVideo() 统一调用，
 * 策略模式支持 Seedance 2.0 / SKYREELS-V4 等不同引擎（ADR-6）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorService {

    private final StoryboardRepository storyboardRepository;
    private final EpisodeRepository episodeRepository;
    private final ModelCallService modelCallService;
    private final ReferenceResolutionService refService;
    private final SseService sseService;
    private final PipelineStateService pipelineStateService;
    private final FFmpegUtils ffmpegUtils;

    /**
     * 生成整集视频（异步执行）
     * 优先一次生成整集，不支持时回退逐镜头 + FFmpeg 拼接
     */
    @Async("videoTaskExecutor")
    public void generateFullVideoAsync(Long projectId, Long episodeId) {
        log.info("开始生成整集视频: projectId={}, episodeId={}", projectId, episodeId);
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("剧集", episodeId));

        try {
            List<Storyboard> storyboards = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
            if (storyboards.isEmpty()) {
                sseService.pushNotification("director-error", "没有找到分镜数据");
                return;
            }

            sseService.pushNotification("director-progress",
                    String.format("正在生成第%s集视频...", episode.getEpisodeNumber()));

            // Try full episode generation first with multi-image references
            boolean fullSuccess = tryFullEpisodeGeneration(episode, storyboards);

            if (!fullSuccess) {
                // Fallback: per-shot generation + FFmpeg concat (ADR-13)
                sseService.pushNotification("director-progress", "整集生成不支持，切换到逐镜头模式...");
                generateAllShotsSequentially(storyboards);
            }

            sseService.pushNotification("director-completed",
                    String.format("第%s集视频生成完成", episode.getEpisodeNumber()));
            log.info("整集视频生成完成: episodeId={}", episodeId);

        } catch (ModelCallException e) {
            log.error("视频生成失败: {}", e.getMessage(), e);
            sseService.pushNotification("director-error", "视频生成失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("视频生成异常: {}", e.getMessage(), e);
            sseService.pushNotification("director-error", "视频生成异常: " + e.getMessage());
        }
    }

    /**
     * 逐镜头生成视频片段（异步执行）
     */
    @Async("videoTaskExecutor")
    public void generateShotVideoAsync(Long storyboardId) {
        log.info("开始生成单镜头视频: storyboardId={}", storyboardId);
        Storyboard sb = storyboardRepository.findById(storyboardId)
                .orElseThrow(() -> new ResourceNotFoundException("分镜", storyboardId));

        try {
            sseService.pushNotification("director-progress",
                    String.format("正在生成分镜 #%d 的视频...", sb.getSequence() + 1));

            sb.setStatus(Storyboard.StoryboardStatus.VIDEO_GENERATING);
            storyboardRepository.save(sb);

            Long projectId = refService.resolveProjectIdFromStoryboard(sb);
            String videoPrompt = buildVideoPrompt(sb, projectId);
            String referenceImage = sb.getGeneratedImageUrl();

            // Call video model via ModelCallService (ADR-6: model-agnostic)
            String videoUrl = modelCallService.callVideo(videoPrompt, referenceImage, null);

            sb.setGeneratedVideoUrl(videoUrl);
            sb.setStatus(Storyboard.StoryboardStatus.VIDEO_DONE);
            storyboardRepository.save(sb);

            pipelineStateService.markDirty(projectId, Project.PipelineStage.DIRECTOR);

            sseService.pushNotification("director-progress",
                    String.format("分镜 #%d 视频生成完成", sb.getSequence() + 1));
            log.info("单镜头视频生成完成: storyboardId={}", storyboardId);

        } catch (ModelCallException e) {
            log.error("单镜头视频生成失败: {}", e.getMessage(), e);
            sb.setStatus(Storyboard.StoryboardStatus.ERROR);
            storyboardRepository.save(sb);
            sseService.pushNotification("director-error",
                    String.format("分镜 #%d 视频生成失败: %s", sb.getSequence() + 1, e.getMessage()));
        }
    }

    /**
     * FFmpeg 拼接视频片段（同步执行）
     * @return 合成后视频 URL，片段列表为空时返回 null
     */
    public String concatVideoFragments(List<String> fragmentUrls) {
        if (fragmentUrls == null || fragmentUrls.isEmpty()) {
            log.warn("FFmpeg 拼接跳过: 视频片段列表为空");
            return null;
        }
        log.info("开始 FFmpeg 拼接: fragmentCount={}", fragmentUrls.size());
        sseService.pushNotification("director-progress",
                String.format("正在用 FFmpeg 拼接 %d 个视频片段...", fragmentUrls.size()));
        try {
            Path workDir = Files.createTempDirectory("ffmpeg_concat_");
            String outputPath = workDir + "/concat_result.mp4";
            ffmpegUtils.concatVideos(fragmentUrls, outputPath);
            log.info("FFmpeg 拼接完成: {}", outputPath);
            return outputPath;
        } catch (Exception e) {
            log.error("FFmpeg 拼接失败: {}", e.getMessage(), e);
            sseService.pushNotification("director-error", "FFmpeg拼接失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 拼接指定剧集的所有视频片段
     */
    public String concatVideosForEpisode(Long episodeId) {
        List<Storyboard> storyboards = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
        List<String> videoUrls = storyboards.stream()
                .filter(sb -> sb.getGeneratedVideoUrl() != null)
                .map(Storyboard::getGeneratedVideoUrl)
                .toList();
        return concatVideoFragments(videoUrls);
    }

    /**
     * 获取视频生成进度
     */
    public VideoStatus getVideoStatus(Long episodeId) {
        List<Storyboard> storyboards = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
        long total = storyboards.size();
        long videoDone = storyboards.stream()
                .filter(sb -> sb.getGeneratedVideoUrl() != null)
                .count();
        long videoError = storyboards.stream()
                .filter(sb -> sb.getStatus() == Storyboard.StoryboardStatus.ERROR)
                .count();
        long videoGenerating = storyboards.stream()
                .filter(sb -> sb.getStatus() == Storyboard.StoryboardStatus.VIDEO_GENERATING)
                .count();

        VideoStatus vs = new VideoStatus();
        vs.setTotalShots((int) total);
        vs.setVideoDone((int) videoDone);
        vs.setVideoError((int) videoError);
        vs.setVideoGenerating((int) videoGenerating);
        vs.setProgress(total > 0 ? (int) ((videoDone * 100) / total) : 0);
        return vs;
    }

    // Inner DTO
    @lombok.Data
    public static class VideoStatus {
        private int totalShots;
        private int videoDone;
        private int videoError;
        private int videoGenerating;
        private int progress; // 0-100
    }

    // ==================== Private Methods ====================

    private boolean tryFullEpisodeGeneration(Episode episode, List<Storyboard> storyboards) {
        try {
            log.info("尝试整集视频生成: episodeId={}, shots={}", episode.getId(), storyboards.size());

            // Collect all reference images (max 9 for Seedance 2.0 compatibility)
            List<String> refImages = storyboards.stream()
                    .filter(sb -> sb.getGeneratedImageUrl() != null)
                    .map(Storyboard::getGeneratedImageUrl)
                    .limit(9)
                    .toList();

            if (refImages.isEmpty()) {
                log.warn("没有分镜图作为参考，无法生成整集视频");
                return false;
            }

            String fullPrompt = buildFullEpisodePrompt(episode, storyboards);
            String videoUrl = modelCallService.callVideo(fullPrompt,
                    refImages.isEmpty() ? null : refImages.get(0), null);

            if (videoUrl != null) {
                // Mark all storyboards as video done
                for (Storyboard sb : storyboards) {
                    sb.setGeneratedVideoUrl(videoUrl);
                    sb.setStatus(Storyboard.StoryboardStatus.VIDEO_DONE);
                }
                storyboardRepository.saveAll(storyboards);

                // Mark director dirty
                Long projectId = refService.resolveProjectIdFromStoryboard(storyboards.get(0));
                if (projectId != null) {
                    pipelineStateService.markDirty(projectId, Project.PipelineStage.DIRECTOR);
                }

                return true;
            }
        } catch (ModelCallException e) {
            log.info("整集生成不支持，回退逐镜头: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 逐镜头顺序生成（方法名准确反映实际行为：顺序而非并发）
     */
    private void generateAllShotsSequentially(List<Storyboard> storyboards) {
        int total = storyboards.size();
        for (int i = 0; i < storyboards.size(); i++) {
            Storyboard sb = storyboards.get(i);
            try {
                sseService.pushNotification("director-progress",
                        String.format("逐镜头生成中 (%d/%d)...", i + 1, total));
                generateShotVideoInternal(sb);
            } catch (Exception e) {
                log.error("镜头 #{} 视频生成失败: {}", sb.getSequence(), e.getMessage());
            }
        }
    }

    /**
     * 单镜头视频生成的内部实现（不走 AOP 代理，供同 Bean 内部调用）
     */
    private void generateShotVideoInternal(Storyboard sb) {
        try {
            sb.setStatus(Storyboard.StoryboardStatus.VIDEO_GENERATING);
            storyboardRepository.save(sb);

            Long projectId = refService.resolveProjectIdFromStoryboard(sb);
            String videoPrompt = buildVideoPrompt(sb, projectId);
            String referenceImage = sb.getGeneratedImageUrl();
            String videoUrl = modelCallService.callVideo(videoPrompt, referenceImage, null);

            sb.setGeneratedVideoUrl(videoUrl);
            sb.setStatus(Storyboard.StoryboardStatus.VIDEO_DONE);
            storyboardRepository.save(sb);

            pipelineStateService.markDirty(projectId, Project.PipelineStage.DIRECTOR);

            sseService.pushNotification("director-progress",
                    String.format("分镜 #%d 视频生成完成", sb.getSequence() + 1));
        } catch (ModelCallException e) {
            log.error("单镜头视频生成失败: storyboardId={}, error={}", sb.getId(), e.getMessage());
            sb.setStatus(Storyboard.StoryboardStatus.ERROR);
            storyboardRepository.save(sb);
            sseService.pushNotification("director-error",
                    String.format("分镜 #%d 视频生成失败: %s", sb.getSequence() + 1, e.getMessage()));
        }
    }

    /**
     * 构建单镜头视频提示词（注入角色锚点描述和场景信息）
     * 使用预解析的 projectId 避免多次反查数据库
     */
    private String buildVideoPrompt(Storyboard sb, Long projectId) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Comic manga animation, ");

        // Inject character references — 使用预解析 projectId
        List<Character> characters = refService.resolveInvolvedCharacters(sb, projectId);
        for (Character ch : characters) {
            if (ch.getAnchorPrompt() != null && !ch.getAnchorPrompt().isEmpty()) {
                prompt.append("character [").append(ch.getName()).append("]: ").append(ch.getAnchorPrompt()).append(", ");
            } else if (ch.getAppearance() != null && !ch.getAppearance().isEmpty()) {
                prompt.append("character [").append(ch.getName()).append("]: ").append(ch.getAppearance()).append(", ");
            }
        }

        // Inject scene reference — 使用预解析 projectId
        Scene scene = refService.resolveInvolvedScene(sb, projectId);
        if (scene != null) {
            prompt.append("scene [").append(scene.getName()).append("]: ");
            if (scene.getDescription() != null && !scene.getDescription().isEmpty()) {
                prompt.append(scene.getDescription()).append(", ");
            }
            if (scene.getStyleHint() != null && !scene.getStyleHint().isEmpty()) {
                prompt.append(scene.getStyleHint()).append(", ");
            }
        }

        if (sb.getAction() != null && !sb.getAction().isEmpty()) {
            prompt.append(sb.getAction()).append(", ");
        }

        if (sb.getCameraMovement() != null && sb.getCameraMovement() != Storyboard.CameraMovement.STATIC) {
            prompt.append(getMovementDescription(sb.getCameraMovement())).append(", ");
        }

        if (sb.getEmotion() != null && !sb.getEmotion().isEmpty()) {
            prompt.append(sb.getEmotion()).append(" atmosphere, ");
        }

        prompt.append("smooth animation, high quality, anime style");
        return prompt.toString();
    }

    private String buildFullEpisodePrompt(Episode episode, List<Storyboard> storyboards) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Full episode animation, ");

        String sceneDesc = storyboards.stream()
                .filter(sb -> sb.getAction() != null && !sb.getAction().isEmpty())
                .map(Storyboard::getAction)
                .limit(3)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
        prompt.append(sceneDesc).append(", ");

        prompt.append("coherent scenes, smooth transitions, cinematic quality, anime style");
        return prompt.toString();
    }

    private String getMovementDescription(Storyboard.CameraMovement cm) {
        switch (cm) {
            case PAN_LEFT: return "pan left";
            case PAN_RIGHT: return "pan right";
            case TILT_UP: return "tilt up";
            case TILT_DOWN: return "tilt down";
            case ZOOM_IN: return "zoom in";
            case ZOOM_OUT: return "zoom out";
            case TRACKING: return "tracking shot";
            case CRANE: return "crane shot";
            case HANDHELD: return "handheld camera";
            default: return "";
        }
    }
}