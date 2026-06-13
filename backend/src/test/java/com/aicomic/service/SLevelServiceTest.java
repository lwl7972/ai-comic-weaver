package com.aicomic.service;

import com.aicomic.common.util.FFmpegUtils;
import com.aicomic.dto.CompositeRequest;
import com.aicomic.entity.AudioTrack;
import com.aicomic.entity.Storyboard;
import com.aicomic.repository.AudioTrackRepository;
import com.aicomic.repository.StoryboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SLevelService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SLevelServiceTest {

    @Mock
    private StoryboardRepository storyboardRepository;

    @Mock
    private AudioTrackRepository audioTrackRepository;

    @Mock
    private FFmpegUtils ffmpegUtils;

    @Mock
    private SseService sseService;

    @InjectMocks
    private SLevelService sLevelService;

    private CompositeRequest compositeRequest;
    private List<Storyboard> storyboards;

    @BeforeEach
    void setUp() {
        compositeRequest = new CompositeRequest();
        compositeRequest.setEpisodeId(1L);
        compositeRequest.setAddSubtitles(true);
        compositeRequest.setMixAudio(true);
        compositeRequest.setTransitionType("fade");
        compositeRequest.setTransitionDuration(1.0);

        storyboards = new ArrayList<>();
        Storyboard sb1 = new Storyboard();
        sb1.setId(1L);
        sb1.setEpisodeId(1L);
        sb1.setSequence(0);
        sb1.setTimeRange("0-4s");
        sb1.setDialogue("[主角，开心]:\"你好世界\"");
        sb1.setGeneratedVideoUrl("/videos/scene1.mp4");
        storyboards.add(sb1);

        Storyboard sb2 = new Storyboard();
        sb2.setId(2L);
        sb2.setEpisodeId(1L);
        sb2.setSequence(1);
        sb2.setTimeRange("4-8s");
        sb2.setDialogue("[配角，惊讶]:\"真的吗？\"");
        sb2.setGeneratedVideoUrl("/videos/scene2.mp4");
        storyboards.add(sb2);
    }

    @Test
    void testFormatSrtTime() throws Exception {
        java.lang.reflect.Method method = SLevelService.class.getDeclaredMethod("formatSrtTime", int.class);
        method.setAccessible(true);
        
        assertEquals("00:00:00,000", method.invoke(sLevelService, 0));
        assertEquals("00:00:04,000", method.invoke(sLevelService, 4));
        assertEquals("00:01:00,000", method.invoke(sLevelService, 60));
        assertEquals("01:00:00,000", method.invoke(sLevelService, 3600));
        assertEquals("01:01:01,000", method.invoke(sLevelService, 3661));
    }

    @Test
    void testParseTimeRangeDuration() throws Exception {
        java.lang.reflect.Method method = SLevelService.class.getDeclaredMethod("parseTimeRangeDuration", String.class);
        method.setAccessible(true);
        
        assertEquals(4, method.invoke(sLevelService, "0-4s"));
        assertEquals(5, method.invoke(sLevelService, "2-7s"));
        assertEquals(4, method.invoke(sLevelService, "4s"));
        assertEquals(4, method.invoke(sLevelService, null));
        assertEquals(4, method.invoke(sLevelService, ""));
    }

    @Test
    void testCleanDialogue() throws Exception {
        java.lang.reflect.Method method = SLevelService.class.getDeclaredMethod("cleanDialogue", String.class);
        method.setAccessible(true);
        
        assertEquals("你好世界", method.invoke(sLevelService, "[主角，开心]:\"你好世界\""));
        assertEquals("真的吗？", method.invoke(sLevelService, "[配角，惊讶]:\"真的吗？\""));
        assertEquals("", method.invoke(sLevelService, null));
        assertEquals("", method.invoke(sLevelService, ""));
    }

    @Test
    void testGenerateSrtFile() throws Exception {
        Path workDir = Files.createTempDirectory("slevel_test");
        try {
            java.lang.reflect.Method method = SLevelService.class.getDeclaredMethod(
                    "generateSrtFile", List.class, String.class
            );
            method.setAccessible(true);
            
            String srtPath = (String) method.invoke(sLevelService, storyboards, workDir.toString());

            assertNotNull(srtPath);
            assertTrue(Files.exists(Paths.get(srtPath)));

            String content = Files.readString(Paths.get(srtPath));
            assertTrue(content.contains("1"));
            assertTrue(content.contains("00:00:00,000 --> 00:00:04,000"));
            assertTrue(content.contains("你好世界"));
        } finally {
            Files.deleteIfExists(Paths.get(workDir.toString(), "subtitles.srt"));
            Files.deleteIfExists(workDir);
        }
    }

    @Test
    void testParseTimeRangeDuration() {
        assertEquals(4, sLevelService.parseTimeRangeDuration("0-4s"));
        assertEquals(5, sLevelService.parseTimeRangeDuration("2-7s"));
        assertEquals(4, sLevelService.parseTimeRangeDuration("4s"));
        assertEquals(4, sLevelService.parseTimeRangeDuration(null));
        assertEquals(4, sLevelService.parseTimeRangeDuration(""));
    }

    @Test
    void testCleanDialogue() {
        String dialogue1 = "[主角，开心]:\"你好世界\"";
        assertEquals("你好世界", sLevelService.cleanDialogue(dialogue1));

        String dialogue2 = "[配角，惊讶]:\"真的吗？\"";
        assertEquals("真的吗？", sLevelService.cleanDialogue(dialogue2));

        assertEquals("", sLevelService.cleanDialogue(null));
        assertEquals("", sLevelService.cleanDialogue(""));
    }

    @Test
    void testCompositeFinalVideo_Success() {
        lenient().when(storyboardRepository.findByEpisodeIdOrderBySequenceAsc(1L))
                .thenReturn(storyboards);
        lenient().when(audioTrackRepository.findByEpisodeIdOrderByCreatedAtAsc(1L))
                .thenReturn(new ArrayList<>());
        lenient().when(ffmpegUtils.concatVideos(anyList(), anyString()))
                .thenReturn(new FFmpegUtils.FFmpegResult(0, "success"));
        lenient().when(ffmpegUtils.addSubtitles(anyString(), anyString(), anyString()))
                .thenReturn(new FFmpegUtils.FFmpegResult(0, "success"));
        lenient().when(ffmpegUtils.addTransition(anyString(), anyString(), anyDouble(), anyString()))
                .thenReturn(new FFmpegUtils.FFmpegResult(0, "success"));

        sLevelService.compositeFinalVideoAsync(1L, compositeRequest);

        verify(storyboardRepository).findByEpisodeIdOrderBySequenceAsc(1L);
        verify(ffmpegUtils).concatVideos(anyList(), anyString());
        verify(ffmpegUtils).addSubtitles(anyString(), anyString(), anyString());
        verify(ffmpegUtils).addTransition(anyString(), eq("fade"), eq(1.0), anyString());
        verify(sseService, atLeastOnce()).pushNotification(anyString(), anyString());
    }

    @Test
    void testCompositeFinalVideo_NoVideoStoryboards() {
        List<Storyboard> emptyStoryboards = new ArrayList<>();
        when(storyboardRepository.findByEpisodeIdOrderBySequenceAsc(1L))
                .thenReturn(emptyStoryboards);

        sLevelService.compositeFinalVideoAsync(1L, compositeRequest);

        verify(sseService).pushNotification(eq("slevel-error"), argThat(msg -> msg.contains("没有可用的分镜视频片段")));
    }

    @Test
    void testGenerateSrtFile() throws IOException {
        Path workDir = Files.createTempDirectory("slevel_test");
        try {
            String srtPath = sLevelService.generateSrtFile(storyboards, workDir.toString());

            assertNotNull(srtPath);
            assertTrue(Files.exists(Paths.get(srtPath)));

            String content = Files.readString(Paths.get(srtPath));
            assertTrue(content.contains("1"));
            assertTrue(content.contains("00:00:00,000 --> 00:00:04,000"));
            assertTrue(content.contains("你好世界"));
        } finally {
            Files.deleteIfExists(Paths.get(workDir.toString(), "subtitles.srt"));
            Files.deleteIfExists(workDir);
        }
    }

    @Test
    void testBuildAudioInputs() throws Exception {
        Long episodeId = 1L;
        List<Long> trackIds = List.of(1L, 2L);
        Path workDir = Files.createTempDirectory("audio_test");

        AudioTrack track1 = new AudioTrack();
        track1.setId(1L);
        track1.setEpisodeId(episodeId);
        track1.setType(AudioTrack.AudioTrackType.BGM);
        track1.setName("背景音乐");
        track1.setFilePath("/audio/bgm.mp3");
        track1.setVolume(0.8);

        AudioTrack track2 = new AudioTrack();
        track2.setId(2L);
        track2.setEpisodeId(episodeId);
        track2.setType(AudioTrack.AudioTrackType.VOICEOVER);
        track2.setName("配音");
        track2.setFilePath("/audio/voice.mp3");
        track2.setVolume(1.0);

        when(audioTrackRepository.findById(1L)).thenReturn(Optional.of(track1));
        when(audioTrackRepository.findById(2L)).thenReturn(Optional.of(track2));

        java.lang.reflect.Method method = SLevelService.class.getDeclaredMethod(
                "buildAudioInputs", Long.class, List.class, Path.class
        );
        method.setAccessible(true);

        try {
            List<FFmpegUtils.AudioInput> inputs = (List<FFmpegUtils.AudioInput>) method.invoke(
                    sLevelService, episodeId, trackIds, workDir
            );

            assertEquals(2, inputs.size());
            assertEquals(0.8, inputs.get(0).getVolume());
            assertEquals(1.0, inputs.get(1).getVolume());
        } finally {
            Files.deleteIfExists(workDir);
        }
    }
}
