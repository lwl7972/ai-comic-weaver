package com.aicomic.service;

import com.aicomic.service.queue.VideoGenerationTask;
import com.aicomic.service.queue.VideoTaskQueueManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 视频队列管理服务
 * 负责：视频生成队列的暂停、恢复、取消、状态查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoQueueManagementService {

    private final VideoTaskQueueManager queueManager;

    /**
     * 暂停队列
     */
    public void pauseQueue() {
        log.info("暂停视频生成队列");
        queueManager.setPaused(true);
    }

    /**
     * 恢复队列
     */
    public void resumeQueue() {
        log.info("恢复视频生成队列");
        queueManager.setPaused(false);
    }

    /**
     * 取消正在进行的任务
     *
     * @param episodeId 剧集 ID
     */
    public void cancelTask(Long episodeId) {
        log.info("取消视频生成任务：episodeId={}", episodeId);
        VideoGenerationTask task = queueManager.getTask(episodeId);
        if (task != null) {
            task.setCancelled(true);
            queueManager.removeTask(episodeId);
        }
    }

    /**
     * 获取任务状态
     *
     * @param episodeId 剧集 ID
     * @return 任务状态
     */
    public VideoGenerationTask.TaskStatus getTaskStatus(Long episodeId) {
        VideoGenerationTask task = queueManager.getTask(episodeId);
        if (task == null) {
            return VideoGenerationTask.TaskStatus.NOT_FOUND;
        }
        return task.getStatus();
    }

    /**
     * 获取所有任务状态
     *
     * @return 任务状态 Map
     */
    public Map<Long, VideoGenerationTask> getAllTasks() {
        return queueManager.getAllTasks();
    }

    /**
     * 获取队列中的任务数量
     *
     * @return 任务数量
     */
    public int getQueueSize() {
        return queueManager.getQueueSize();
    }

    /**
     * 清除已完成的任务
     */
    public void clearCompletedTasks() {
        log.info("清除已完成的任务");
        queueManager.clearCompletedTasks();
    }
}
