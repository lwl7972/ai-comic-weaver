package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.common.util.FFmpegUtils;
import com.aicomic.dto.VideoGenerationRequest;
import com.aicomic.entity.Episode;
import com.aicomic.entity.PipelineState;
import com.aicomic.entity.Project;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.EpisodeRepository;
import com.aicomic.repository.StoryboardRepository;
import com.aicomic.service.model.ModelCallException;
import com.aicomic.service.model.ModelCallService;
import com.aicomic.service.queue.VideoGenerationTask;
import com.aicomic.service.queue.VideoTaskQueueManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectorServiceTest {

    @Mock
    private StoryboardRepository storyboardRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private ModelCallService modelCallService;

    @Mock
    private ReferenceResolutionService refService;

    @Mock
    private SseService sseService;

    @Mock
    private PipelineStateService pipelineStateService;

    @Mock
    private FFmpegUtils ffmpegUtils;

    @Mock
    private VideoTaskQueueManager queueManager;

    @InjectMocks
    private DirectorService directorService;

    private Episode testEpisode;
    private List<Storyboard> testStoryboards;

    @BeforeEach
    void setUp() {
        testEpisode = new Episode();
        testEpisode.setId(1L);
        testEpisode.setEpisodeNumber(1);
        testEpisode.setTitle("第一集");

        testStoryboards = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Storyboard sb = new Storyboard();
            sb.setId((long) (i + 1));
            sb.setEpisodeId(1L);
            sb.setSequence(i);
            sb.setAction("动作描述 " + i);
            sb.setGeneratedImageUrl("/images/shot" + (i + 1) + ".jpg");
            sb.setStatus(Storyboard.StoryboardStatus.IMAGE_DONE);
            testStoryboards.add(sb);
        }
    }

    @Test
    void testGetVideoStatus_WithAllDone() {
        when(storyboardRepository.findByEpisodeIdOrderBySequenceAsc(1L)).thenReturn(testStoryboards);

        for (Storyboard sb : testStoryboards) {
            sb.setGeneratedVideoUrl("/videos/shot" + sb.getSequence() + ".mp4");
        }

        DirectorService.VideoStatus status = directorService.getVideoStatus(1L);

        assertEquals(3, status.getTotalShots());
        assertEquals(3, status.getVideoDone());
        assertEquals(0, status.getVideoError());
        assertEquals(0, status.getVideoGenerating());
        assertEquals(100, status.getProgress());
    }

    @Test
    void testGetVideoStatus_WithPartialDone() {
        testStoryboards.get(0).setGeneratedVideoUrl("/videos/shot1.mp4");
        testStoryboards.get(1).setStatus(Storyboard.StoryboardStatus.VIDEO_GENERATING);
        testStoryboards.get(2).setStatus(Storyboard.StoryboardStatus.ERROR);

        when(storyboardRepository.findByEpisodeIdOrderBySequenceAsc(1L)).thenReturn(testStoryboards);

        DirectorService.VideoStatus status = directorService.getVideoStatus(1L);

        assertEquals(3, status.getTotalShots());
        assertEquals(1, status.getVideoDone());
        assertEquals(1, status.getVideoError());
        assertEquals(1, status.getVideoGenerating());
        assertEquals(33, status.getProgress());
    }

    @Test
    void testGetVideoStatus_WithEmptyStoryboards() {
        when(storyboardRepository.findByEpisodeIdOrderBySequenceAsc(1L)).thenReturn(new ArrayList<>());

        DirectorService.VideoStatus status = directorService.getVideoStatus(1L);

        assertEquals(0, status.getTotalShots());
        assertEquals(0, status.getProgress());
    }

    @Test
    void testConcatVideoFragments_WithEmptyList() throws Exception {
        String result = directorService.concatVideoFragments(new ArrayList<>());

        assertNull(result);
        verify(ffmpegUtils, never()).concatVideos(anyList(), anyString());
    }

    @Test
    void testConcatVideoFragments_WithValidFragments() throws Exception {
        List<String> fragments = List.of("/videos/shot1.mp4", "/videos/shot2.mp4");
        String outputPath = "/tmp/ffmpeg_concat/concat_result.mp4";

        when(ffmpegUtils.concatVideos(anyList(), anyString()))
                .thenAnswer(invocation -> {
                    String out = invocation.getArgument(1);
                    return new FFmpegUtils.FFmpegResult(0, "Success");
                });

        String result = directorService.concatVideoFragments(fragments);

        assertNotNull(result);
        assertEquals(outputPath, result);
        verify(ffmpegUtils).concatVideos(eq(fragments), anyString());
        verify(sseService).pushNotification(eq("director-progress"), contains("拼接"));
    }

    @Test
    void testConcatVideoFragments_WithFFmpegFailure() throws Exception {
        List<String> fragments = List.of("/videos/shot1.mp4");

        when(ffmpegUtils.concatVideos(anyList(), anyString()))
                .thenThrow(new IOException("FFmpeg error"));

        String result = directorService.concatVideoFragments(fragments);

        assertNull(result);
        verify(sseService).pushNotification(eq("director-error"), contains("FFmpeg 拼接失败"));
    }

    @Test
    void testPauseQueue() {
        directorService.pauseQueue();
        verify(queueManager).pause();
        verify(sseService).pushNotification("director-queue", "视频生成队列已暂停");
    }

    @Test
    void testResumeQueue() {
        directorService.resumeQueue();
        verify(queueManager).resume();
        verify(sseService).pushNotification("director-queue", "视频生成队列已恢复");
    }

    @Test
    void testCancelTask_Success() {
        String taskId = "task-123";
        when(queueManager.cancelTask(taskId)).thenReturn(true);

        boolean result = directorService.cancelTask(taskId);

        assertTrue(result);
        verify(sseService).pushNotification("director-task-cancelled", "任务已取消：" + taskId);
    }

    @Test
    void testCancelTask_Failure() {
        String taskId = "task-123";
        when(queueManager.cancelTask(taskId)).thenReturn(false);

        boolean result = directorService.cancelTask(taskId);

        assertFalse(result);
    }

    @Test
    void testSubmitVideoGeneration_SingleShot() {
        VideoGenerationRequest request = new VideoGenerationRequest();
        request.setProjectId(1L);
        request.setEpisodeId(1L);
        request.setGenerationMode("SINGLE_SHOT");
        request.setPriority("HIGH");

        VideoGenerationTask task = mock(VideoGenerationTask.class);
        when(task.getTaskId()).thenReturn("task-456");
        when(queueManager.submitTask(any(VideoGenerationTask.class))).thenReturn(task);

        VideoGenerationRequest result = directorService.submitVideoGeneration(request);

        assertEquals("task-456", result.getTaskId());
        verify(queueManager).submitTask(any(VideoGenerationTask.class));
    }

    @Test
    void testSubmitVideoGeneration_FullEpisode() {
        VideoGenerationRequest request = new VideoGenerationRequest();
        request.setProjectId(1L);
        request.setEpisodeId(1L);
        request.setGenerationMode("FULL_EPISODE");

        VideoGenerationTask task = mock(VideoGenerationTask.class);
        when(task.getTaskId()).thenReturn("task-789");
        when(queueManager.submitTask(any(VideoGenerationTask.class))).thenReturn(task);

        VideoGenerationRequest result = directorService.submitVideoGeneration(request);

        assertEquals("task-789", result.getTaskId());
    }

    @Test
    void testSubmitVideoGeneration_InvalidPriority() {
        VideoGenerationRequest request = new VideoGenerationRequest();
        request.setEpisodeId(1L);
        request.setPriority("INVALID_PRIORITY");

        VideoGenerationTask task = mock(VideoGenerationTask.class);
        when(task.getTaskId()).thenReturn("task-999");
        when(queueManager.submitTask(any(VideoGenerationTask.class))).thenReturn(task);

        VideoGenerationRequest result = directorService.submitVideoGeneration(request);

        assertNotNull(result.getTaskId());
    }

    @Test
    void testTryFullEpisodeGeneration_Success() throws Exception {
        when(modelCallService.callVideo(anyString(), anyString(), isNull()))
                .thenReturn("/videos/full_episode.mp4");
        when(pipelineStateService.markDirty(anyLong(), any(Project.PipelineStage.class)))
                .thenReturn(new PipelineState());

    }

    @Test
    void testTryFullEpisodeGeneration_NoReferenceImages() {
        for (Storyboard sb : testStoryboards) {
            sb.setGeneratedImageUrl(null);
        }

    }

    @Test
    void testTryFullEpisodeGeneration_ModelCallFailed() throws Exception {
        when(modelCallService.callVideo(anyString(), anyString(), isNull()))
                .thenThrow(new ModelCallException("Model unavailable"));

    }
}
