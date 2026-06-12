package com.aicomic.service;

import com.aicomic.common.util.FFmpegUtils;
import com.aicomic.dto.CompositeRequest;
import com.aicomic.dto.ExportConfig;
import com.aicomic.dto.WatermarkConfig;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.StoryboardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S级模块服务
 * 负责：成片合成、字幕叠加、音画同步、视频导出（MP4/MOV）、水印添加
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SLevelService {

    private final StoryboardRepository storyboardRepository;
    private final FFmpegUtils ffmpegUtils;
    private final SseService sseService;

    @Value("${app.storage.path:./data}")
    private String storageBasePath;

    /**
     * 成片合成（异步执行）
     * FFmpeg 拼接 + 字幕叠加 + 音频混合 + 转场特效
     */
    @Async("videoTaskExecutor")
    public void compositeFinalVideoAsync(Long projectId, CompositeRequest request) {
        log.info("开始成片合成: projectId={}, episodeId={}", projectId, request.getEpisodeId());

        Path workDir = null;
        try {
            // 1. 收集分镜视频片段
            sseService.pushNotification("slevel-progress", "正在收集视频片段...");

            List<Storyboard> storyboards = storyboardRepository
                    .findByEpisodeIdOrderBySequenceAsc(request.getEpisodeId());

            List<Storyboard> videoStoryboards = new ArrayList<>();
            for (Storyboard sb : storyboards) {
                if (sb.getGeneratedVideoUrl() != null && !sb.getGeneratedVideoUrl().isBlank()) {
                    videoStoryboards.add(sb);
                }
            }

            if (videoStoryboards.isEmpty()) {
                throw new RuntimeException("没有可用的分镜视频片段，episodeId=" + request.getEpisodeId());
            }

            log.info("收集到 {} 个分镜视频片段", videoStoryboards.size());

            // 2. 创建工作目录
            workDir = Paths.get(storageBasePath, "projects", String.valueOf(projectId), "output");
            Files.createDirectories(workDir);

            // 3. 下载/解析视频路径并拼接
            sseService.pushNotification("slevel-progress", "正在拼接视频（FFmpeg concat）...");

            List<String> videoPaths = new ArrayList<>();
            for (Storyboard sb : videoStoryboards) {
                String localPath = downloadToTemp(sb.getGeneratedVideoUrl(), workDir.toString());
                videoPaths.add(localPath);
            }

            Path concatOutput = workDir.resolve("concat.mp4");
            FFmpegUtils.FFmpegResult concatResult = ffmpegUtils.concatVideos(videoPaths, concatOutput.toString());
            log.info("视频拼接完成: {}", concatOutput);

            // 当前处理的视频路径（逐步替换）
            String currentVideo = concatOutput.toString();

            // 4. 字幕叠加
            if (request.isAddSubtitles()) {
                sseService.pushNotification("slevel-progress", "正在生成SRT字幕文件...");

                String srtPath = generateSrtFile(videoStoryboards, workDir.toString());

                sseService.pushNotification("slevel-progress", "正在烧录字幕...");

                Path subtitleOutput = workDir.resolve("subtitle.mp4");
                ffmpegUtils.addSubtitles(currentVideo, srtPath, subtitleOutput.toString());
                currentVideo = subtitleOutput.toString();
                log.info("字幕烧录完成: {}", subtitleOutput);
            }

            // 5. 音频混合（暂跳过，后续音频库任务启用）
            if (request.isMixAudio()) {
                sseService.pushNotification("slevel-progress", "音频混合暂未启用，跳过...");
                log.info("音频混合暂未启用，跳过");
            }

            // 6. 转场特效
            if (request.getTransitionType() != null && !request.getTransitionType().isBlank()) {
                sseService.pushNotification("slevel-progress",
                        "正在添加转场特效(" + request.getTransitionType() + ")...");

                Path transitionOutput = workDir.resolve("transition.mp4");
                ffmpegUtils.addTransition(currentVideo, request.getTransitionType(),
                        request.getTransitionDuration(), transitionOutput.toString());
                currentVideo = transitionOutput.toString();
                log.info("转场特效添加完成: {}", transitionOutput);
            }

            // 7. 复制最终文件
            sseService.pushNotification("slevel-progress", "正在生成最终成片...");

            String finalFileName = "episode-" + request.getEpisodeId() + "-final.mp4";
            Path finalOutput = workDir.resolve(finalFileName);
            Files.copy(Paths.get(currentVideo), finalOutput, StandardCopyOption.REPLACE_EXISTING);

            log.info("成片合成完成: {}", finalOutput);
            sseService.pushNotification("slevel-completed",
                    "成片合成完成: " + finalOutput.getFileName());

        } catch (Exception e) {
            log.error("成片合成失败: projectId={}, episodeId={}, error={}",
                    projectId, request.getEpisodeId(), e.getMessage(), e);
            sseService.pushNotification("slevel-error", "成片合成失败: " + e.getMessage());
        }
    }

    /**
     * 导出视频（异步执行）
     * FFmpeg 格式转码
     */
    @Async("videoTaskExecutor")
    public void exportVideoAsync(Long projectId, Long episodeId, ExportConfig config) {
        log.info("开始导出视频: projectId={}, episodeId={}, format={}, resolution={}",
                projectId, episodeId, config.getFormat(), config.getResolution());

        try {
            // 1. 读取合成后的最终视频
            Path workDir = Paths.get(storageBasePath, "projects", String.valueOf(projectId), "output");
            String finalVideoName = "episode-" + episodeId + "-final.mp4";
            Path finalVideo = workDir.resolve(finalVideoName);

            if (!Files.exists(finalVideo)) {
                throw new RuntimeException("最终成片不存在: " + finalVideo + "，请先执行成片合成");
            }

            sseService.pushNotification("slevel-progress",
                    String.format("正在导出视频 (%s, %s)...", config.getFormat(), config.getResolution()));

            // 2. FFmpeg 转码
            String exportFileName = "episode-" + episodeId + "-export." + config.getFormat();
            Path exportOutput = workDir.resolve(exportFileName);

            ffmpegUtils.transcode(finalVideo.toString(), config, exportOutput.toString());

            log.info("视频导出完成: {}", exportOutput);
            sseService.pushNotification("slevel-completed",
                    "视频导出完成: " + exportOutput.getFileName());

        } catch (Exception e) {
            log.error("视频导出失败: projectId={}, episodeId={}, error={}",
                    projectId, episodeId, e.getMessage(), e);
            sseService.pushNotification("slevel-error", "视频导出失败: " + e.getMessage());
        }
    }

    /**
     * 添加水印（异步执行）
     * FFmpeg overlay/drawtext 滤镜
     */
    @Async("taskExecutor")
    public void addWatermarkAsync(Long projectId, Long episodeId, WatermarkConfig config) {
        log.info("开始添加水印: projectId={}, episodeId={}, type={}",
                projectId, episodeId, config.getType());

        try {
            // 1. 读取合成后的最终视频
            Path workDir = Paths.get(storageBasePath, "projects", String.valueOf(projectId), "output");
            String finalVideoName = "episode-" + episodeId + "-final.mp4";
            Path finalVideo = workDir.resolve(finalVideoName);

            if (!Files.exists(finalVideo)) {
                throw new RuntimeException("最终成片不存在: " + finalVideo + "，请先执行成片合成");
            }

            sseService.pushNotification("slevel-progress",
                    String.format("正在添加%s水印...", "IMAGE".equalsIgnoreCase(config.getType()) ? "图片" : "文字"));

            // 2. FFmpeg 添加水印
            String watermarkFileName = "episode-" + episodeId + "-watermark.mp4";
            Path watermarkOutput = workDir.resolve(watermarkFileName);

            ffmpegUtils.addWatermark(finalVideo.toString(), config, watermarkOutput.toString());

            log.info("水印添加完成: {}", watermarkOutput);
            sseService.pushNotification("slevel-completed",
                    "水印添加完成: " + watermarkOutput.getFileName());

        } catch (Exception e) {
            log.error("水印添加失败: projectId={}, episodeId={}, error={}",
                    projectId, episodeId, e.getMessage(), e);
            sseService.pushNotification("slevel-error", "水印添加失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从分镜对话生成SRT字幕文件
     *
     * @param storyboards 分镜列表（按sequence排序）
     * @param workDir     工作目录
     * @return SRT文件路径
     */
    private String generateSrtFile(List<Storyboard> storyboards, String workDir) throws IOException {
        Path srtPath = Paths.get(workDir, "subtitles.srt");
        StringBuilder srtContent = new StringBuilder();

        int cumulativeSeconds = 0;
        int index = 1;

        for (Storyboard sb : storyboards) {
            if (sb.getDialogue() == null || sb.getDialogue().isBlank()) {
                // 没有对话的分镜，根据timeRange推进时间
                int duration = parseTimeRangeDuration(sb.getTimeRange());
                cumulativeSeconds += duration;
                continue;
            }

            // 解析本分镜时长
            int duration = parseTimeRangeDuration(sb.getTimeRange());

            int startSeconds = cumulativeSeconds;
            int endSeconds = cumulativeSeconds + duration;

            // SRT 序号
            srtContent.append(index++).append("\n");
            // 时间轴
            srtContent.append(formatSrtTime(startSeconds))
                    .append(" --> ")
                    .append(formatSrtTime(endSeconds))
                    .append("\n");
            // 字幕文本（清理格式标记）
            String cleanDialogue = cleanDialogue(sb.getDialogue());
            srtContent.append(cleanDialogue).append("\n\n");

            cumulativeSeconds += duration;
        }

        Files.writeString(srtPath, srtContent.toString());
        log.info("SRT字幕文件生成完成: {}, 共 {} 条字幕", srtPath, index - 1);
        return srtPath.toString();
    }

    /**
     * 格式化时间为SRT格式: HH:MM:SS,000
     *
     * @param totalSeconds 总秒数
     * @return SRT时间格式字符串
     */
    String formatSrtTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d,000", hours, minutes, seconds);
    }

    /**
     * 解析timeRange获取时长（秒）
     * 支持格式: "0-4s", "2-6s", "0-3.5s" 等
     *
     * @param timeRange 时间范围字符串
     * @return 时长（秒），默认4秒
     */
    private int parseTimeRangeDuration(String timeRange) {
        if (timeRange == null || timeRange.isBlank()) {
            return 4; // 默认4秒
        }

        // 匹配 "起始-结束s" 格式，如 "0-4s", "2-6s"
        Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)s?");
        Matcher matcher = pattern.matcher(timeRange.trim());
        if (matcher.find()) {
            double start = Double.parseDouble(matcher.group(1));
            double end = Double.parseDouble(matcher.group(2));
            return (int) Math.ceil(end - start);
        }

        // 尝试匹配纯数字秒数，如 "4s"
        Pattern simplePattern = Pattern.compile("(\\d+(?:\\.\\d+)?)s");
        Matcher simpleMatcher = simplePattern.matcher(timeRange.trim());
        if (simpleMatcher.find()) {
            return (int) Math.ceil(Double.parseDouble(simpleMatcher.group(1)));
        }

        return 4; // 默认4秒
    }

    /**
     * 清理对话文本中的格式标记
     * 去除 [角色名, 情绪]: 等前缀，只保留台词内容
     *
     * @param dialogue 原始对话文本
     * @return 清理后的纯文本
     */
    private String cleanDialogue(String dialogue) {
        if (dialogue == null) {
            return "";
        }
        // 去除 [xxx, yyy]:" 前缀格式
        String cleaned = dialogue.replaceAll("\\[.*?\\]\\s*:\\s*\"", "");
        // 去除末尾引号
        cleaned = cleaned.replaceAll("\"$", "");
        // 去除换行，SRT中换行用 \N
        cleaned = cleaned.replace("\n", " ");
        return cleaned.trim();
    }

    /**
     * 处理URL转本地路径
     * 如果是本地路径直接返回，如果是URL则下载到工作目录
     *
     * @param url     视频URL或本地路径
     * @param workDir 工作目录
     * @return 本地文件路径
     */
    private String downloadToTemp(String url, String workDir) throws IOException {
        // 已经是本地路径
        if (url.startsWith("/") || url.startsWith("./") || url.contains(":\\") || url.startsWith("file:")) {
            // 处理 file:// 前缀
            if (url.startsWith("file://")) {
                return url.substring(7);
            }
            // 处理 file: 前缀
            if (url.startsWith("file:")) {
                return url.substring(5);
            }
            // Windows 绝对路径如 D:\... 或 C:/...
            if (url.length() > 2 && url.charAt(1) == ':') {
                return url;
            }
            // 相对路径转为绝对路径
            Path localPath = Paths.get(url);
            if (Files.exists(localPath)) {
                return localPath.toAbsolutePath().toString();
            }
            // 尝试基于 storageBasePath 解析
            Path resolved = Paths.get(storageBasePath).resolve(url);
            if (Files.exists(resolved)) {
                return resolved.toAbsolutePath().toString();
            }
            return localPath.toAbsolutePath().toString();
        }

        // HTTP/HTTPS URL - 下载到本地
        if (url.startsWith("http://") || url.startsWith("https://")) {
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            if (fileName.isEmpty() || !fileName.contains(".")) {
                fileName = "video_" + System.currentTimeMillis() + ".mp4";
            }
            Path targetPath = Paths.get(workDir, fileName);

            log.info("下载视频: {} -> {}", url, targetPath);
            try (java.io.InputStream inputStream = new java.net.URL(url).openStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return targetPath.toAbsolutePath().toString();
        }

        // 其他情况，尝试作为本地路径
        return Paths.get(url).toAbsolutePath().toString();
    }
}
