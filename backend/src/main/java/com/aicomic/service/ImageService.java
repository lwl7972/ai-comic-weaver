package com.aicomic.service;

import com.aicomic.common.util.FFmpegUtils;
import com.aicomic.entity.Episode;
import com.aicomic.entity.Project;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.EpisodeRepository;
import com.aicomic.repository.StoryboardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 图片处理服务
 * 提供首帧图网格拼接、缩略图生成等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final StoryboardRepository storyboardRepository;
    private final EpisodeRepository episodeRepository;
    private final FFmpegUtils ffmpegUtils;

    @Value("${app.storage.output-dir:./output}")
    private String outputDir;

    /**
     * 为指定剧集生成首帧图网格（N×N）
     *
     * @param episodeId 剧集 ID
     * @param gridSize 网格大小（3 表示 3×3）
     * @return 网格图片 URL
     */
    public String createEpisodeFirstFrameGrid(Long episodeId, int gridSize) {
        log.info("生成剧集首帧图网格：episodeId={}, gridSize={}×{}", episodeId, gridSize, gridSize);

        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new IllegalArgumentException("剧集不存在：" + episodeId));

        List<Storyboard> storyboards = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
        if (storyboards.isEmpty()) {
            throw new IllegalArgumentException("剧集没有分镜：" + episodeId);
        }

        // 提取已生成的分镜图（作为首帧）
        List<String> firstFrames = storyboards.stream()
                .filter(sb -> sb.getGeneratedImageUrl() != null)
                .map(Storyboard::getGeneratedImageUrl)
                .toList();

        if (firstFrames.isEmpty()) {
            throw new IllegalStateException("剧集没有已生成的分镜图");
        }

        log.info("找到 {} 张分镜图", firstFrames.size());

        try {
            // 创建输出目录
            Path outputDirPath = Paths.get(outputDir, "grids", String.valueOf(episodeId));
            if (!Files.exists(outputDirPath)) {
                Files.createDirectories(outputDirPath);
            }

            // 生成输出文件名
            String outputFileName = "episode_" + episodeId + "_grid_" + gridSize + "x" + gridSize + ".jpg";
            Path outputPath = outputDirPath.resolve(outputFileName);

            // 调用 FFmpeg 生成网格
            ffmpegUtils.createImageGrid(firstFrames, outputPath.toString(), gridSize);

            log.info("首帧图网格生成完成：{}", outputPath);
            return outputPath.toString();

        } catch (IOException e) {
            log.error("创建输出目录失败", e);
            throw new RuntimeException("创建输出目录失败：" + e.getMessage(), e);
        }
    }

    /**
     * 为指定项目生成首帧图网格（包含所有剧集）
     *
     * @param projectId 项目 ID
     * @param gridSize 网格大小
     * @return 网格图片 URL
     */
    public String createProjectFirstFrameGrid(Long projectId, int gridSize) {
        log.info("生成项目首帧图网格：projectId={}, gridSize={}×{}", projectId, gridSize, gridSize);

        // 查找项目的所有剧集
        List<Episode> episodes = episodeRepository.findByProjectIdOrderByEpisodeNumberAsc(projectId);
        if (episodes.isEmpty()) {
            throw new IllegalArgumentException("项目没有剧集：" + projectId);
        }

        // 收集所有剧集的第一张分镜图
        List<String> firstFrames = new java.util.ArrayList<>();
        for (Episode episode : episodes) {
            List<Storyboard> storyboards = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episode.getId());
            if (!storyboards.isEmpty() && storyboards.get(0).getGeneratedImageUrl() != null) {
                firstFrames.add(storyboards.get(0).getGeneratedImageUrl());
            }
        }

        if (firstFrames.isEmpty()) {
            throw new IllegalStateException("项目没有已生成的分镜图");
        }

        log.info("找到 {} 张剧集首帧图", firstFrames.size());

        try {
            Path outputDirPath = Paths.get(outputDir, "grids", "project_" + projectId);
            if (!Files.exists(outputDirPath)) {
                Files.createDirectories(outputDirPath);
            }

            String outputFileName = "project_" + projectId + "_grid_" + gridSize + "x" + gridSize + ".jpg";
            Path outputPath = outputDirPath.resolve(outputFileName);

            ffmpegUtils.createImageGrid(firstFrames, outputPath.toString(), gridSize);

            log.info("项目首帧图网格生成完成：{}", outputPath);
            return outputPath.toString();

        } catch (IOException e) {
            log.error("创建输出目录失败", e);
            throw new RuntimeException("创建输出目录失败：" + e.getMessage(), e);
        }
    }
}
