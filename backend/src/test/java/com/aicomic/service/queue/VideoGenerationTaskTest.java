package com.aicomic.service.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VideoGenerationTaskTest {

    @Test
    void testConstructor_FullEpisode() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.HIGH);

        assertNotNull(task.getTaskId());
        assertEquals(1L, task.getProjectId());
        assertEquals(2L, task.getEpisodeId());
        assertEquals(VideoGenerationTask.TaskType.FULL_EPISODE, task.getTaskType());
        assertEquals(VideoGenerationTask.Priority.HIGH, task.getPriority());
        assertEquals(VideoGenerationTask.TaskStatus.PENDING, task.getStatus());
        assertEquals(0, task.getProgress());
        assertEquals(0, task.getRetryCount());
        assertEquals(3, task.getMaxRetries());
        assertNotNull(task.getSubmittedAt());
    }

    @Test
    void testConstructor_SingleShot() {
        VideoGenerationTask task = new VideoGenerationTask(3L, VideoGenerationTask.Priority.MEDIUM);

        assertNotNull(task.getTaskId());
        assertEquals(3L, task.getStoryboardId());
        assertEquals(VideoGenerationTask.TaskType.SINGLE_SHOT, task.getTaskType());
        assertEquals(VideoGenerationTask.Priority.MEDIUM, task.getPriority());
    }

    @Test
    void testMarkStarted() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);

        task.markStarted();

        assertEquals(VideoGenerationTask.TaskStatus.RUNNING, task.getStatus());
        assertNotNull(task.getStartedAt());
    }

    @Test
    void testMarkCompleted() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);

        task.markCompleted("/videos/result.mp4");

        assertEquals(VideoGenerationTask.TaskStatus.SUCCESS, task.getStatus());
        assertEquals(100, task.getProgress());
        assertEquals("/videos/result.mp4", task.getVideoUrl());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void testMarkFailed() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);

        task.markFailed("Model API error");

        assertEquals(VideoGenerationTask.TaskStatus.FAILED, task.getStatus());
        assertEquals("Model API error", task.getErrorMessage());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void testUpdateProgress() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);

        task.markStarted();
        task.updateProgress(30);
        assertEquals(30, task.getProgress());

        task.updateProgress(75);
        assertEquals(75, task.getProgress());

        task.updateProgress(150);
        assertEquals(100, task.getProgress());
    }

    @Test
    void testUpdateProgress_BeforeStarted() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);

        task.updateProgress(50);
        assertEquals(0, task.getProgress());
    }

    @Test
    void testCancel_Pending() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);

        boolean result = task.cancel();

        assertTrue(result);
        assertTrue(task.isCancelled());
        assertEquals(VideoGenerationTask.TaskStatus.CANCELLED, task.getStatus());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void testCancel_Running() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);
        task.markStarted();

        boolean result = task.cancel();

        assertTrue(result);
        assertTrue(task.isCancelled());
        assertEquals(VideoGenerationTask.TaskStatus.CANCELLED, task.getStatus());
    }

    @Test
    void testCancel_AlreadyCompleted() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);
        task.markCompleted("/videos/result.mp4");

        boolean result = task.cancel();

        assertFalse(result);
        assertEquals(VideoGenerationTask.TaskStatus.SUCCESS, task.getStatus());
    }

    @Test
    void testCancel_AlreadyFailed() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);
        task.markFailed("Error");

        boolean result = task.cancel();

        assertFalse(result);
        assertEquals(VideoGenerationTask.TaskStatus.FAILED, task.getStatus());
    }

    @Test
    void testIsCancelled() {
        VideoGenerationTask task = new VideoGenerationTask(1L, 2L, VideoGenerationTask.TaskType.FULL_EPISODE, VideoGenerationTask.Priority.LOW);

        assertFalse(task.isCancelled());

        task.cancel();
        assertTrue(task.isCancelled());
    }

    @Test
    void testPriorityWeight() {
        assertEquals(3, VideoGenerationTask.Priority.HIGH.getWeight());
        assertEquals(2, VideoGenerationTask.Priority.MEDIUM.getWeight());
        assertEquals(1, VideoGenerationTask.Priority.LOW.getWeight());
    }

    @Test
    void testTaskIdUnique() {
        VideoGenerationTask task1 = new VideoGenerationTask(1L, VideoGenerationTask.Priority.LOW);
        VideoGenerationTask task2 = new VideoGenerationTask(1L, VideoGenerationTask.Priority.LOW);

        assertNotEquals(task1.getTaskId(), task2.getTaskId());
    }
}
