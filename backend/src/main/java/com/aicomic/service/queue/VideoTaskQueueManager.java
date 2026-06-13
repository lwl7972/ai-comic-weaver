package com.aicomic.service.queue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频生成队列管理器
 * 负责任务调度、优先级管理、队列控制
 */
@Slf4j
@Component
public class VideoTaskQueueManager {

    /** 任务队列 - 按优先级排序 */
    private final PriorityQueue<VideoGenerationTask> taskQueue;

    /** 所有任务（包括正在执行的） */
    private final Map<String, VideoGenerationTask> allTasks;

    /** 当前正在执行的任务 */
    private final Map<String, VideoGenerationTask> runningTasks;

    /** 队列是否暂停 */
    private final AtomicBoolean paused;

    /** 队列调度器 */
    private final ScheduledExecutorService scheduler;

    /** 最大并发任务数 */
    private final int maxConcurrentTasks;

    /** 队列管理器是否已启动 */
    private boolean started;

    public VideoTaskQueueManager() {
        this.taskQueue = new PriorityQueue<>(
                Comparator.comparingInt(task -> -task.getPriority().getWeight())
                        .thenComparing(VideoGenerationTask::getSubmittedAt)
        );
        this.allTasks = new ConcurrentHashMap<>();
        this.runningTasks = new ConcurrentHashMap<>();
        this.paused = new AtomicBoolean(false);
        this.maxConcurrentTasks = 2; // 最多同时执行 2 个视频生成任务
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.started = false;
    }

    /**
     * 启动队列调度器
     */
    public void start() {
        if (!started) {
            started = true;
            // 每 2 秒检查一次队列
            scheduler.scheduleAtFixedRate(this::processQueue, 0, 2, TimeUnit.SECONDS);
            log.info("视频生成队列管理器已启动");
        }
    }

    /**
     * 停止队列调度器
     */
    public void shutdown() {
        started = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("视频生成队列管理器已停止");
    }

    /**
     * 提交任务到队列
     */
    public VideoGenerationTask submitTask(VideoGenerationTask task) {
        log.info("提交视频生成任务：taskId={}, type={}, priority={}",
                task.getTaskId(), task.getTaskType(), task.getPriority());

        allTasks.put(task.getTaskId(), task);

        synchronized (taskQueue) {
            taskQueue.offer(task);
        }

        return task;
    }

    /**
     * 处理队列 - 调度任务执行
     */
    private void processQueue() {
        if (paused.get()) {
            log.debug("队列已暂停，跳过任务处理");
            return;
        }

        // 检查是否有可用的执行槽位
        int availableSlots = maxConcurrentTasks - runningTasks.size();
        if (availableSlots <= 0) {
            log.debug("无可用执行槽位：running={}, max={}", runningTasks.size(), maxConcurrentTasks);
            return;
        }

        // 获取下一个可执行的任务
        VideoGenerationTask task = getNextExecutableTask();
        if (task != null) {
            executeTask(task);
        }
    }

    /**
     * 获取下一个可执行的任务
     */
    private VideoGenerationTask getNextExecutableTask() {
        synchronized (taskQueue) {
            while (!taskQueue.isEmpty()) {
                VideoGenerationTask task = taskQueue.poll();

                // 检查任务是否被取消
                if (task.isCancelled()) {
                    log.info("跳过已取消的任务：taskId={}", task.getTaskId());
                    allTasks.remove(task.getTaskId());
                    continue;
                }

                // 检查任务状态
                if (task.getStatus() != VideoGenerationTask.TaskStatus.PENDING) {
                    log.debug("跳过非等待状态的任务：taskId={}, status={}", task.getTaskId(), task.getStatus());
                    continue;
                }

                return task;
            }
        }
        return null;
    }

    /**
     * 执行任务
     */
    private void executeTask(VideoGenerationTask task) {
        log.info("开始执行视频生成任务：taskId={}, type={}", task.getTaskId(), task.getTaskType());

        task.markStarted();
        runningTasks.put(task.getTaskId(), task);

        // 创建异步执行
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                // 任务执行逻辑由调用方提供
                // 这里仅做状态管理
                log.info("任务执行完成：taskId={}", task.getTaskId());
            } catch (Exception e) {
                log.error("任务执行异常：taskId={}", task.getTaskId(), e);
                task.markFailed(e.getMessage());
            } finally {
                runningTasks.remove(task.getTaskId());
            }
        });

        task.setFuture(future);
    }

    /**
     * 暂停队列
     */
    public void pause() {
        if (paused.compareAndSet(false, true)) {
            log.info("视频生成队列已暂停");
        }
    }

    /**
     * 恢复队列
     */
    public void resume() {
        if (paused.compareAndSet(true, false)) {
            log.info("视频生成队列已恢复");
        }
    }

    /**
     * 检查队列是否暂停
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        VideoGenerationTask task = allTasks.get(taskId);
        if (task == null) {
            log.warn("任务不存在：taskId={}", taskId);
            return false;
        }

        boolean cancelled = task.cancel();

        // 如果任务正在运行，尝试中断
        if (task.getFuture() != null && !task.getFuture().isDone()) {
            task.getFuture().cancel(true);
            runningTasks.remove(taskId);
        }

        log.info("任务取消{}: taskId={}", cancelled ? "成功" : "失败", taskId);
        return cancelled;
    }

    /**
     * 获取任务状态
     */
    public VideoGenerationTask getTask(String taskId) {
        return allTasks.get(taskId);
    }

    /**
     * 获取所有任务
     */
    public List<VideoGenerationTask> getAllTasks() {
        return List.copyOf(allTasks.values());
    }

    /**
     * 获取等待中的任务
     */
    public List<VideoGenerationTask> getPendingTasks() {
        return allTasks.values().stream()
                .filter(t -> t.getStatus() == VideoGenerationTask.TaskStatus.PENDING)
                .toList();
    }

    /**
     * 获取正在运行的任务
     */
    public List<VideoGenerationTask> getRunningTasks() {
        return List.copyOf(runningTasks.values());
    }

    /**
     * 获取队列统计信息
     */
    public QueueStats getQueueStats() {
        QueueStats stats = new QueueStats();
        stats.setPendingCount(taskQueue.size());
        stats.setRunningCount(runningTasks.size());
        stats.setPaused(paused.get());
        stats.setMaxConcurrent(maxConcurrentTasks);

        long completed = allTasks.values().stream()
                .filter(t -> t.getStatus() == VideoGenerationTask.TaskStatus.SUCCESS)
                .count();
        long failed = allTasks.values().stream()
                .filter(t -> t.getStatus() == VideoGenerationTask.TaskStatus.FAILED)
                .count();
        long cancelled = allTasks.values().stream()
                .filter(t -> t.getStatus() == VideoGenerationTask.TaskStatus.CANCELLED)
                .count();

        stats.setCompletedCount((int) completed);
        stats.setFailedCount((int) failed);
        stats.setCancelledCount((int) cancelled);
        stats.setTotalCount(allTasks.size());

        return stats;
    }

    /**
     * 队列统计信息
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
