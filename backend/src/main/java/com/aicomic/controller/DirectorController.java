package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.VideoGenerationRequest;
import com.aicomic.dto.VideoGenerationResponse;
import com.aicomic.service.DirectorService;
import com.aicomic.service.queue.VideoGenerationTask;
import com.aicomic.service.queue.VideoTaskQueueManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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

    /** POST /api/v1/episodes/{id}/generate-videos-custom - 自定义参数生成整集视频 */
    @PostMapping("/episodes/{id}/generate-videos-custom")
    public ApiResponse<VideoGenerationRequest> generateFullVideoCustom(
            @PathVariable Long id,
            @RequestBody VideoGenerationRequest request
    ) {
        return ApiResponse.success(directorService.submitVideoGeneration(request));
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

    /** POST /api/v1/director/queue/pause - 暂停视频生成队列 */
    @PostMapping("/director/queue/pause")
    public ApiResponse<Void> pauseQueue() {
        directorService.pauseQueue();
        return ApiResponse.success();
    }

    /** POST /api/v1/director/queue/resume - 恢复视频生成队列 */
    @PostMapping("/director/queue/resume")
    public ApiResponse<Void> resumeQueue() {
        directorService.resumeQueue();
        return ApiResponse.success();
    }

    /** GET /api/v1/director/queue/stats - 获取队列统计信息 */
    @GetMapping("/director/queue/stats")
    public ApiResponse<QueueStats> getQueueStats() {
        VideoTaskQueueManager.QueueStats stats = directorService.getQueueStats();
        QueueStats response = new QueueStats();
        response.setPendingCount(stats.getPendingCount());
        response.setRunningCount(stats.getRunningCount());
        response.setCompletedCount(stats.getCompletedCount());
        response.setFailedCount(stats.getFailedCount());
        response.setCancelledCount(stats.getCancelledCount());
        response.setTotalCount(stats.getTotalCount());
        response.setPaused(stats.isPaused());
        response.setMaxConcurrent(stats.getMaxConcurrent());
        return ApiResponse.success(response);
    }

    /** DELETE /api/v1/director/tasks/{taskId} - 取消视频生成任务 */
    @DeleteMapping("/director/tasks/{taskId}")
    public ApiResponse<Void> cancelTask(@PathVariable String taskId) {
        boolean cancelled = directorService.cancelTask(taskId);
        return cancelled ? ApiResponse.success() : ApiResponse.error(500, "任务取消失败或不存在");
    }

    /** GET /api/v1/director/tasks/{taskId} - 获取任务详情 */
    @GetMapping("/director/tasks/{taskId}")
    public ApiResponse<VideoGenerationResponse> getTaskInfo(@PathVariable String taskId) {
        VideoGenerationTask task = directorService.getTaskInfo(taskId);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        VideoGenerationResponse response = convertToResponse(task);
        return ApiResponse.success(response);
    }

    /** GET /api/v1/director/tasks - 获取所有任务列表 */
    @GetMapping("/director/tasks")
    public ApiResponse<List<VideoGenerationResponse>> getAllTasks() {
        List<VideoGenerationTask> tasks = directorService.getAllTasks();
        List<VideoGenerationResponse> responses = tasks.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    /**
     * 转换为响应 DTO
     */
    private VideoGenerationResponse convertToResponse(VideoGenerationTask task) {
        VideoGenerationResponse response = new VideoGenerationResponse();
        response.setTaskId(task.getTaskId());
        response.setTaskType(task.getTaskType().name());
        response.setVideoUrl(task.getVideoUrl());
        response.setStatus(task.getStatus().name());
        response.setPriority(task.getPriority().name());
        response.setProgress(task.getProgress());
        response.setErrorMessage(task.getErrorMessage());
        response.setSubmittedAt(task.getSubmittedAt());
        response.setStartedAt(task.getStartedAt());
        response.setCompletedAt(task.getCompletedAt());
        response.setRetryCount(task.getRetryCount());
        return response;
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

    /**
     * 队列统计响应 DTO
     */
    @lombok.Data
    public static class QueueStats {
        private int pendingCount;
        private int runningCount;
        private int completedCount;
        private int failedCount;
        private int cancelledCount;
        private int totalCount;
        private boolean paused;
        private int maxConcurrent;
    }
}
