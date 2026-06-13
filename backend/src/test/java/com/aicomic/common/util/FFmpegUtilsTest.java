package com.aicomic.common.util;

import com.aicomic.dto.ExportConfig;
import com.aicomic.dto.WatermarkConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FFmpegUtils 单元测试
 */
class FFmpegUtilsTest {

    private FFmpegUtils ffmpegUtils;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ffmpegUtils = new FFmpegUtils();
        ffmpegUtils.ffmpegPath = "ffmpeg";
        ffmpegUtils.timeoutSeconds = 10;
    }

    @Test
    void testAudioInput_DefaultConstructor() {
        FFmpegUtils.AudioInput input = new FFmpegUtils.AudioInput();
        assertNotNull(input);
        assertEquals(1.0, input.getVolume());
    }

    @Test
    void testAudioInput_ParameterizedConstructor() {
        FFmpegUtils.AudioInput input = new FFmpegUtils.AudioInput("/path/to/audio.mp3", 0.8);
        assertEquals("/path/to/audio.mp3", input.getFilePath());
        assertEquals(0.8, input.getVolume());
    }

    @Test
    void testVideoInfo_DefaultValues() {
        FFmpegUtils.VideoInfo info = new FFmpegUtils.VideoInfo();
        assertNotNull(info);
        assertFalse(info.isValid());
    }

    @Test
    void testFFmpegResult() {
        FFmpegUtils.FFmpegResult result = new FFmpegUtils.FFmpegResult(0, "success");
        assertEquals(0, result.getExitCode());
        assertEquals("success", result.getOutput());
    }

    @Test
    void testGetScaleFilter() throws Exception {
        assertNull(getScaleFilter(null));
        assertEquals("scale=1280:720", getScaleFilter("720p"));
        assertEquals("scale=1920:1080", getScaleFilter("1080p"));
        assertEquals("scale=3840:2160", getScaleFilter("4K"));
        assertNull(getScaleFilter("invalid"));
    }

    @Test
    void testGetOverlayPosition() throws Exception {
        String defaultPos = getDefaultOverlayPosition(null);
        assertNotNull(defaultPos);

        assertEquals("10:10", getOverlayPosition("TOP_LEFT"));
        assertEquals("main_w-overlay_w-10:10", getOverlayPosition("TOP_RIGHT"));
        assertEquals("10:main_h-overlay_h-10", getOverlayPosition("BOTTOM_LEFT"));
        assertEquals("main_w-overlay_w-10:main_h-overlay_h-10", getOverlayPosition("BOTTOM_RIGHT"));
        assertEquals("main_w-overlay_w-10:main_h-overlay_h-10", getOverlayPosition("invalid"));
    }

    @Test
    void testConcatVideos_EmptyList() {
        assertThrows(IOException.class, () -> {
            ffmpegUtils.concatVideos(List.of(), tempDir.resolve("output.mp4").toString());
        });
    }

    @Test
    void testCreateImageGrid_EmptyList() {
        assertThrows(IllegalArgumentException.class, () -> {
            ffmpegUtils.createImageGrid(List.of(), tempDir.resolve("grid.jpg").toString(), 3);
        });
    }

    @Test
    void testDrawtextFilterEscaping() throws Exception {
        WatermarkConfig config = new WatermarkConfig();
        config.setType("TEXT");
        config.setContent("Test: 50% off!");
        config.setFontSize(24);
        config.setFontColor("#FFFFFF");
        config.setPosition("bottom-right");

        String filter = buildDrawtextFilter(config);
        assertNotNull(filter);
        assertTrue(filter.contains("drawtext="));
        assertTrue(filter.contains("text="));
    }

    @Test
    void testExportConfig() {
        ExportConfig config = new ExportConfig();
        config.setFormat("mp4");
        config.setResolution("1080p");
        config.setBitrate(8000);
        config.setFps(24);

        assertEquals("mp4", config.getFormat());
        assertEquals("1080p", config.getResolution());
        assertEquals(8000, config.getBitrate());
        assertEquals(24, config.getFps());
    }

    @Test
    void testWatermarkConfig() {
        WatermarkConfig config = new WatermarkConfig();
        config.setType("IMAGE");
        config.setImagePath("/path/to/watermark.png");
        config.setPosition("top-right");
        config.setOpacity(0.5);

        assertEquals("IMAGE", config.getType());
        assertEquals("/path/to/watermark.png", config.getImagePath());
        assertEquals("top-right", config.getPosition());
        assertEquals(0.5, config.getOpacity());
    }

    private String getScaleFilter(String resolution) throws Exception {
        java.lang.reflect.Method method = FFmpegUtils.class.getDeclaredMethod("getScaleFilter", String.class);
        method.setAccessible(true);
        return (String) method.invoke(ffmpegUtils, resolution);
    }

    private String getOverlayPosition(String position) throws Exception {
        java.lang.reflect.Method method = FFmpegUtils.class.getDeclaredMethod("getOverlayPosition", String.class);
        method.setAccessible(true);
        return (String) method.invoke(ffmpegUtils, position);
    }

    private String getDefaultOverlayPosition(String position) throws Exception {
        java.lang.reflect.Method method = FFmpegUtils.class.getDeclaredMethod("getOverlayPosition", String.class);
        method.setAccessible(true);
        return (String) method.invoke(ffmpegUtils, (Object) null);
    }

    private String buildDrawtextFilter(WatermarkConfig config) throws Exception {
        java.lang.reflect.Method method = FFmpegUtils.class.getDeclaredMethod("buildDrawtextFilter", WatermarkConfig.class);
        method.setAccessible(true);
        return (String) method.invoke(ffmpegUtils, config);
    }
}
