package com.aicomic.service.queue;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频生成任务
 */
@Slf4j
@Data
public class VideoGenerationTask {

    /** 任务 ID */
    private final String taskId;

    /** 项目 ID */
    private Long projectId;

    /** 剧集 ID */
    private Long episodeId;

    /** 分镜 ID（单镜头生成时使用） */
    private Long storyboardId;

    /** 任务类型：FULL_EPISODE / SINGLE_SHOT */
    private TaskType taskType;

    /** 优先级：HIGH / MEDIUM / LOW */
    private Priority priority;

    /** 任务状态 */
    private TaskStatus status;

    /** 进度 (0-100) */
    private Integer progress;

    /** 错误信息 */
    private String errorMessage;

    /** 结果视频 URL */
    private String videoUrl;

    /** 提交时间 */
    private final LocalDateTime submittedAt;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 异步任务 Future */
    private CompletableFuture<Void> future;

    /** 取消标志 */
    private final AtomicBoolean cancelled;

    /** 重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetries;

    public VideoGenerationTask(Long projectId, Long episodeId, TaskType taskType, Priority priority) {
        this.taskId = UUID.randomUUID().toString().replace("-", "");
        this.projectId = projectId;
        this.episodeId = episodeId;
        this.taskType = taskType;
        this.priority = priority;
        this.status = TaskStatus.PENDING;
        this.progress = 0;
        this.submittedAt = LocalDateTime.now();
        this.cancelled = new AtomicBoolean(false);
        this.retryCount = 0;
        this.maxRetries = 3;
    }

    public VideoGenerationTask(Long storyboardId, Priority priority) {
        this(null, null, storyboardId, TaskType.SINGLE_SHOT, priority);
    }

    public VideoGenerationTask(Long projectId, Long episodeId, Long storyboardId, TaskType taskType, Priority priority) {
        this.taskId = UUID.randomUUID().toString().replace("-", "");
        this.projectId = projectId;
        this.episodeId = episodeId;
        this.storyboardId = storyboardId;
        this.taskType = taskType;
        this.priority = priority;
        this.status = TaskStatus.PENDING;
        this.progress = 0;
        this.submittedAt = LocalDateTime.now();
        this.cancelled = new AtomicBoolean(false);
        this.retryCount = 0;
        this.maxRetries = 3;
    }

    /**
     * 标记任务开始
     */
    public void markStarted() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 标记任务完成
     */
    public void markCompleted(String videoUrl) {
        this.status = TaskStatus.SUCCESS;
        this.progress = 100;
        this.videoUrl = videoUrl;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 标记任务失败
     */
    public void markFailed(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 更新进度
     */
    public void updateProgress(int progress) {
        if (this.status == TaskStatus.RUNNING) {
            this.progress = Math.min(100, Math.max(0, progress));
        }
    }

    /**
     * 取消任务
     */
    public boolean cancel() {
        if (this.status == TaskStatus.PENDING || this.status == TaskStatus.RUNNING) {
            this.cancelled.set(true);
            this.status = TaskStatus.CANCELLED;
            this.completedAt = LocalDateTime.now();
            log.info("任务已取消：taskId={}", taskId);
            return true;
        }
        return false;
    }

    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    public enum TaskType {
        FULL_EPISODE,  // 整集生成
        SINGLE_SHOT    // 单镜头生成
    }

    public enum Priority {
        HIGH(3),
        MEDIUM(2),
        LOW(1);

        private final int weight;

        Priority(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }
    }

    public enum TaskStatus {
        PENDING,     // 等待中
        RUNNING,     // 执行中
        SUCCESS,     // 成功
        FAILED,      // 失败
        CANCELLED    // 已取消
    }
}
