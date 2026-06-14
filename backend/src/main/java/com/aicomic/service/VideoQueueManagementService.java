package com.aicomic.service;

import com.aicomic.service.queue.VideoGenerationTask;
import com.aicomic.service.queue.VideoTaskQueueManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
        queueManager.pause();
    }

    /**
     * 恢复队列
     */
    public void resumeQueue() {
        log.info("恢复视频生成队列");
        queueManager.resume();
    }

    /**
     * 取消正在进行的任务
     *
     * @param episodeId 剧集 ID
     */
    public void cancelTask(Long episodeId) {
        log.info("取消视频生成任务：episodeId={}", episodeId);
        VideoGenerationTask task = queueManager.getTask(episodeId.toString());
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 获取任务状态
     *
     * @param episodeId 剧集 ID
     * @return 任务状态
     */
    public VideoGenerationTask.TaskStatus getTaskStatus(Long episodeId) {
        VideoGenerationTask task = queueManager.getTask(episodeId.toString());
        if (task == null) {
            return null;
        }
        return task.getStatus();
    }

    /**
     * 获取所有任务状态
     *
     * @return 任务列表
     */
    public List<VideoGenerationTask> getAllTasks() {
        return queueManager.getAllTasks();
    }

    /**
     * 获取队列中的任务数量
     *
     * @return 任务数量
     */
    public int getQueueSize() {
        return queueManager.getQueueStats().getPendingCount();
    }
}
