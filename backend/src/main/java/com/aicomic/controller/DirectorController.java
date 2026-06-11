package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.service.DirectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 🎥 导演模块 REST API - 对应设计文档 6.4 导演/视频生成端点
 * 负责：整集视频生成调度、单镜头回退 + FFmpeg 拼接（ADR-13）
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DirectorController {

    private final DirectorService directorService;

    /** POST /api/v1/episodes/{id}/generate-videos - 生成整集视频（优先一次生成，不支持时回退逐镜头） */
    @PostMapping("/episodes/{id}/generate-videos")
    public ApiResponse<Void> generateFullVideo(@PathVariable Long id) {
        directorService.generateFullVideoAsync(null, id);
        return ApiResponse.success();
    }

    /** POST /api/v1/storyboards/{id}/generate-video - 逐镜头生成视频片段 */
    @PostMapping("/storyboards/{id}/generate-video")
    public ApiResponse<Void> generateShotVideo(@PathVariable Long id) {
        directorService.generateShotVideoAsync(id);
        return ApiResponse.success();
    }

    /** POST /api/v1/episodes/{episodeId}/concat-videos - FFmpeg 拼接视频片段 */
    @PostMapping("/episodes/{episodeId}/concat-videos")
    public ApiResponse<String> concatVideos(@PathVariable Long episodeId) {
        String result = directorService.concatVideosForEpisode(episodeId);
        return ApiResponse.success(result);
    }

    /** GET /api/v1/episodes/{episodeId}/video-status - 查询视频生成进度 */
    @GetMapping("/episodes/{episodeId}/video-status")
    public ApiResponse<VideoStatus> getVideoStatus(@PathVariable Long episodeId) {
        DirectorService.VideoStatus vs = directorService.getVideoStatus(episodeId);
        VideoStatus response = new VideoStatus();
        response.setTotalShots(vs.getTotalShots());
        response.setVideoDone(vs.getVideoDone());
        response.setVideoError(vs.getVideoError());
        response.setVideoGenerating(vs.getVideoGenerating());
        response.setProgress(vs.getProgress());
        return ApiResponse.success(response);
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
}
