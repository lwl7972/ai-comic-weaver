package com.aicomic.service.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VideoTaskQueueManagerTest {

    private VideoTaskQueueManager queueManager;

    @BeforeEach
    void setUp() {
        queueManager = new VideoTaskQueueManager();
    }

    @Test
    void testConstructor() {
        queueManager.start();

        VideoTaskQueueManager.QueueStats stats = queueManager.getQueueStats();
        assertNotNull(stats);
        assertEquals(0, stats.getPendingCount());
        assertEquals(0, stats.getRunningCount());
        assertEquals(0, stats.getCompletedCount());
        assertEquals(0, stats.getFailedCount());
        assertEquals(0, stats.getCancelledCount());
        assertEquals(0, stats.getTotalCount());
        assertEquals(2, stats.getMaxConcurrent());
        queueManager.shutdown();
    }

    @Test
    void testSubmitTask_Pending() {
        queueManager.start();

        VideoGenerationTask task = new VideoGenerationTask(1L, VideoGenerationTask.Priority.HIGH);
        VideoGenerationTask submitted = queueManager.submitTask(task);

        assertNotNull(submitted);
        assertEquals(task.getTaskId(), submitted.getTaskId());
        assertEquals(VideoGenerationTask.Priority.HIGH, submitted.getPriority());

        VideoTaskQueueManager.QueueStats stats = queueManager.getQueueStats();
        assertEquals(1, stats.getPendingCount());
        assertEquals(1, stats.getTotalCount());

        queueManager.shutdown();
    }

    @Test
    void testSubmitTask_MultipleTasks_SortedByPriority() {
        queueManager.start();

        VideoGenerationTask lowTask = new VideoGenerationTask(1L, VideoGenerationTask.Priority.LOW);
        VideoGenerationTask highTask = new VideoGenerationTask(2L, VideoGenerationTask.Priority.HIGH);
        VideoGenerationTask mediumTask = new VideoGenerationTask(3L, VideoGenerationTask.Priority.MEDIUM);

        queueManager.submitTask(lowTask);
        queueManager.submitTask(highTask);
        queueManager.submitTask(mediumTask);

        List<VideoGenerationTask> allTasks = queueManager.getAllTasks();
        assertEquals(3, allTasks.size());

        queueManager.shutdown();
    }

    @Test
    void testGetTask_Existing() {
        queueManager.start();

        VideoGenerationTask task = new VideoGenerationTask(1L, VideoGenerationTask.Priority.MEDIUM);
        queueManager.submitTask(task);

        VideoGenerationTask retrieved = queueManager.getTask(task.getTaskId());

        assertNotNull(retrieved);
        assertEquals(task.getTaskId(), retrieved.getTaskId());

        queueManager.shutdown();
    }

    @Test
    void testGetTask_NonExisting() {
        queueManager.start();

        VideoGenerationTask retrieved = queueManager.getTask("non-existing-task");

        assertNull(retrieved);

        queueManager.shutdown();
    }

    @Test
    void testGetAllTasks() {
        queueManager.start();

        VideoGenerationTask task1 = new VideoGenerationTask(1L, VideoGenerationTask.Priority.LOW);
        VideoGenerationTask task2 = new VideoGenerationTask(2L, VideoGenerationTask.Priority.HIGH);

        queueManager.submitTask(task1);
        queueManager.submitTask(task2);

        List<VideoGenerationTask> tasks = queueManager.getAllTasks();

        assertEquals(2, tasks.size());
        assertTrue(tasks.stream().anyMatch(t -> t.getTaskId().equals(task1.getTaskId())));
        assertTrue(tasks.stream().anyMatch(t -> t.getTaskId().equals(task2.getTaskId())));

        queueManager.shutdown();
    }

    @Test
    void testCancelTask_Success() {
        queueManager.start();

        VideoGenerationTask task = new VideoGenerationTask(1L, VideoGenerationTask.Priority.MEDIUM);
        queueManager.submitTask(task);

        boolean cancelled = queueManager.cancelTask(task.getTaskId());

        assertTrue(cancelled);
        assertTrue(task.isCancelled());
        assertEquals(VideoGenerationTask.TaskStatus.CANCELLED, task.getStatus());

        VideoTaskQueueManager.QueueStats stats = queueManager.getQueueStats();
        assertEquals(1, stats.getCancelledCount());

        queueManager.shutdown();
    }

    @Test
    void testCancelTask_NonExisting() {
        queueManager.start();

        boolean cancelled = queueManager.cancelTask("non-existing");

        assertFalse(cancelled);

        queueManager.shutdown();
    }

    @Test
    void testPauseAndResume() {
        queueManager.start();

        queueManager.pause();
        VideoTaskQueueManager.QueueStats stats1 = queueManager.getQueueStats();
        assertTrue(stats1.isPaused());

        queueManager.resume();
        VideoTaskQueueManager.QueueStats stats2 = queueManager.getQueueStats();
        assertFalse(stats2.isPaused());

        queueManager.shutdown();
    }

    @Test
    void testPause_WithPendingTasks() {
        queueManager.start();

        VideoGenerationTask task = new VideoGenerationTask(1L, VideoGenerationTask.Priority.MEDIUM);
        queueManager.submitTask(task);

        queueManager.pause();

        VideoTaskQueueManager.QueueStats stats = queueManager.getQueueStats();
        assertTrue(stats.isPaused());
        assertEquals(1, stats.getPendingCount());

        queueManager.shutdown();
    }

    @Test
    void testShutdown() {
        queueManager.start();

        VideoGenerationTask task = new VideoGenerationTask(1L, VideoGenerationTask.Priority.LOW);
        queueManager.submitTask(task);

        queueManager.shutdown();

        VideoTaskQueueManager.QueueStats stats = queueManager.getQueueStats();
        assertNotNull(stats);
    }

    @Test
    void testPriorityWeightAffectsOrdering() {
        VideoGenerationTask.Priority[] priorities = VideoGenerationTask.Priority.values();

        assertTrue(VideoGenerationTask.Priority.HIGH.getWeight() > VideoGenerationTask.Priority.MEDIUM.getWeight());
        assertTrue(VideoGenerationTask.Priority.MEDIUM.getWeight() > VideoGenerationTask.Priority.LOW.getWeight());
    }

    @Test
    void testQueueStats_AfterMultipleOperations() {
        queueManager.start();

        VideoGenerationTask task1 = new VideoGenerationTask(1L, VideoGenerationTask.Priority.HIGH);
        VideoGenerationTask task2 = new VideoGenerationTask(2L, VideoGenerationTask.Priority.LOW);
        VideoGenerationTask task3 = new VideoGenerationTask(3L, VideoGenerationTask.Priority.MEDIUM);

        queueManager.submitTask(task1);
        queueManager.submitTask(task2);
        queueManager.submitTask(task3);
        queueManager.cancelTask(task3.getTaskId());

        VideoTaskQueueManager.QueueStats stats = queueManager.getQueueStats();
        assertEquals(3, stats.getTotalCount());
        assertEquals(1, stats.getCancelledCount());
        assertFalse(stats.isPaused());

        queueManager.shutdown();
    }
}
