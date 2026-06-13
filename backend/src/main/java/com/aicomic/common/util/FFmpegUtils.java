package com.aicomic.common.util;

import com.aicomic.dto.ExportConfig;
import com.aicomic.dto.WatermarkConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FFmpeg 命令行工具类
 * 提供视频拼接、字幕烧录、音频混合、转场效果、格式转码、水印叠加等能力
 */
@Slf4j
@Component
public class FFmpegUtils {

    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${ffmpeg.timeout-seconds:300}")
    private int timeoutSeconds;

    // ==================== 内部 DTO ====================

    /** 音频输入 */
    @Data
    public static class AudioInput {
        private String filePath;
        private double volume = 1.0;

        public AudioInput() {}

        public AudioInput(String filePath, double volume) {
            this.filePath = filePath;
            this.volume = volume;
        }
    }

    /** 视频元信息 */
    @Data
    public static class VideoInfo {
        private String filePath;
        private boolean valid;
        private double duration;
        private int width;
        private int height;
    }

    /** FFmpeg 执行结果 */
    @Data
    public static class FFmpegResult {
        private int exitCode;
        private String output;

        public FFmpegResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    // ==================== 公共方法 ====================

    /**
     * 使用 concat demuxer 拼接多个视频
     *
     * @param videoPaths 待拼接的视频文件路径列表
     * @param outputPath 输出文件路径
     * @return FFmpegResult 执行结果
     */
    public FFmpegResult concatVideos(List<String> videoPaths, String outputPath) throws IOException, InterruptedException {
        // 生成 concat 列表文件
        Path concatFile = Files.createTempFile("ffmpeg_concat_", ".txt");
        try {
            StringBuilder sb = new StringBuilder();
            for (String path : videoPaths) {
                // 路径中的特殊字符需要转义
                String escaped = path.replace("'", "'\\''");
                sb.append("file '").append(escaped).append("'\n");
            }
            Files.writeString(concatFile, sb.toString(), StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-y");
            command.add("-f");
            command.add("concat");
            command.add("-safe");
            command.add("0");
            command.add("-i");
            command.add(concatFile.toString());
            command.add("-c");
            command.add("copy");
            command.add(outputPath);

            return execute(command);
        } finally {
            Files.deleteIfExists(concatFile);
        }
    }

    /**
     * 烧录 SRT 字幕到视频
     *
     * @param videoPath  输入视频路径
     * @param srtPath    SRT 字幕文件路径
     * @param outputPath 输出文件路径
     * @return FFmpegResult 执行结果
     */
    public FFmpegResult addSubtitles(String videoPath, String srtPath, String outputPath) throws IOException, InterruptedException {
        // subtitles 滤镜中路径的反斜杠和冒号需要转义
        String escapedSrt = srtPath
                .replace("\\", "/")
                .replace(":", "\\:");

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(videoPath);
        command.add("-vf");
        command.add("subtitles=" + escapedSrt);
        command.add("-c:a");
        command.add("copy");
        command.add(outputPath);

        return execute(command);
    }

    /**
     * 多音轨混合
     *
     * @param videoPath   输入视频路径
     * @param audioTracks 音频轨道列表
     * @param outputPath  输出文件路径
     * @return FFmpegResult 执行结果
     */
    public FFmpegResult mixAudio(String videoPath, List<AudioInput> audioTracks, String outputPath) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(videoPath);

        // 添加每个音频输入
        for (AudioInput track : audioTracks) {
            command.add("-i");
            command.add(track.getFilePath());
        }

        // 构建 amix 滤镜
        StringBuilder filterComplex = new StringBuilder();
        // 原视频音频为 [0:a]
        filterComplex.append("[0:a]");
        for (int i = 0; i < audioTracks.size(); i++) {
            filterComplex.append("[").append(i + 1).append(":a]volume=")
                    .append(audioTracks.get(i).getVolume()).append("[a").append(i).append("];");
        }
        // amix 合并所有音轨
        filterComplex.append("[0:a]");
        for (int i = 0; i < audioTracks.size(); i++) {
            filterComplex.append("[a").append(i).append("]");
        }
        filterComplex.append("amix=inputs=").append(1 + audioTracks.size())
                .append(":duration=first:dropout_transition=2[aout]");

        command.add("-filter_complex");
        command.add(filterComplex.toString());
        command.add("-map");
        command.add("0:v");
        command.add("-map");
        command.add("[aout]");
        command.add("-c:v");
        command.add("copy");
        command.add(outputPath);

        return execute(command);
    }

    /**
     * 添加转场效果
     *
     * @param inputPath      输入视频路径
     * @param transitionType 转场类型: fade / slideleft / slideup / zoom
     * @param duration       转场时长(秒)
     * @param outputPath     输出文件路径
     * @return FFmpegResult 执行结果
     */
    public FFmpegResult addTransition(String inputPath, String transitionType, double duration, String outputPath) throws IOException, InterruptedException {
        // 先获取视频时长
        VideoInfo info = getVideoInfo(inputPath);
        double videoDuration = info.getDuration();

        String filter;
        switch (transitionType.toLowerCase()) {
            case "fade":
                filter = String.format("fade=t=in:st=0:d=%.2f,fade=t=out:st=%.2f:d=%.2f",
                        duration, videoDuration - duration, duration);
                break;
            case "slideleft":
                filter = String.format("fade=t=in:st=0:d=%.2f:color=black,fade=t=out:st=%.2f:d=%.2f:color=black",
                        duration, videoDuration - duration, duration);
                break;
            case "slideup":
                filter = String.format("fade=t=in:st=0:d=%.2f:color=black,fade=t=out:st=%.2f:d=%.2f:color=black",
                        duration, videoDuration - duration, duration);
                break;
            case "zoom":
                filter = String.format("zoompan=z='min(zoom+0.0015,1.5)':d=1:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s=iw*ih:fps=30,fade=t=in:st=0:d=%.2f,fade=t=out:st=%.2f:d=%.2f",
                        duration, videoDuration - duration, duration);
                break;
            default:
                throw new IllegalArgumentException("不支持的转场类型: " + transitionType);
        }

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(inputPath);
        command.add("-vf");
        command.add(filter);
        command.add("-c:a");
        command.add("copy");
        command.add(outputPath);

        return execute(command);
    }

    /**
     * 格式转码
     *
     * @param inputPath  输入视频路径
     * @param config     导出配置
     * @param outputPath 输出文件路径
     * @return FFmpegResult 执行结果
     */
    public FFmpegResult transcode(String inputPath, ExportConfig config, String outputPath) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(inputPath);

        // 视频编码器
        String videoCodec;
        switch (config.getFormat().toLowerCase()) {
            case "mov":
                videoCodec = "prores_ks";
                break;
            case "avi":
                videoCodec = "mpeg4";
                break;
            case "mp4":
            default:
                videoCodec = "libx264";
                break;
        }
        command.add("-c:v");
        command.add(videoCodec);

        // 分辨率
        String scaleFilter = getScaleFilter(config.getResolution());
        if (scaleFilter != null) {
            command.add("-vf");
            command.add(scaleFilter);
        }

        // 码率
        if (config.getBitrate() != null) {
            command.add("-b:v");
            command.add(config.getBitrate() + "k");
        }

        // 帧率
        if (config.getFps() != null) {
            command.add("-r");
            command.add(String.valueOf(config.getFps()));
        }

        command.add(outputPath);
        return execute(command);
    }

    /**
     * 叠加水印(文字/图片)
     *
     * @param inputPath 输入视频路径
     * @param config    水印配置
     * @param outputPath 输出文件路径
     * @return FFmpegResult 执行结果
     */
    public FFmpegResult addWatermark(String inputPath, WatermarkConfig config, String outputPath) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-i");
        command.add(inputPath);

        if ("IMAGE".equalsIgnoreCase(config.getType())) {
            // 图片水印
            command.add("-i");
            command.add(config.getImagePath());

            String overlayPosition = getOverlayPosition(config.getPosition());
            String overlayFilter = overlayPosition;
            if (config.getOpacity() != null && config.getOpacity() < 1.0) {
                overlayFilter = String.format("[1:v]format=rgba,colorchannelmixer=aa=%.2f[wm];[0:v][wm]overlay=%s",
                        config.getOpacity(), overlayPosition);
            } else {
                overlayFilter = String.format("[0:v][1:v]overlay=%s", overlayPosition);
            }

            command.add("-filter_complex");
            command.add(overlayFilter);
            command.add("-c:a");
            command.add("copy");
        } else {
            // 文字水印
            String drawtextFilter = buildDrawtextFilter(config);
            command.add("-vf");
            command.add(drawtextFilter);
            command.add("-c:a");
            command.add("copy");
        }

        command.add(outputPath);
        return execute(command);
    }

    /**
     * 获取视频元信息(使用 ffprobe)
     *
     * @param videoPath 视频文件路径
     * @return VideoInfo 视频元信息
     */
    public VideoInfo getVideoInfo(String videoPath) throws IOException, InterruptedException {
        String ffprobePath = ffmpegPath.replace("ffmpeg", "ffprobe");

        List<String> command = new ArrayList<>();
        command.add(ffprobePath);
        command.add("-v");
        command.add("error");
        command.add("-show_entries");
        command.add("format=duration:stream=width,height");
        command.add("-of");
        command.add("default=noprint_wrappers=1");
        command.add(videoPath);

        FFmpegResult result = execute(command);

        VideoInfo info = new VideoInfo();
        info.setFilePath(videoPath);
        info.setValid(true);

        // 解析输出
        String output = result.getOutput();
        Pattern durationPattern = Pattern.compile("duration=([\\d.]+)");
        Pattern widthPattern = Pattern.compile("width=(\\d+)");
        Pattern heightPattern = Pattern.compile("height=(\\d+)");

        Matcher durationMatcher = durationPattern.matcher(output);
        if (durationMatcher.find()) {
            info.setDuration(Double.parseDouble(durationMatcher.group(1)));
        }

        Matcher widthMatcher = widthPattern.matcher(output);
        if (widthMatcher.find()) {
            info.setWidth(Integer.parseInt(widthMatcher.group(1)));
        }

        Matcher heightMatcher = heightPattern.matcher(output);
        if (heightMatcher.find()) {
            info.setHeight(Integer.parseInt(heightMatcher.group(1)));
        }

        return info;
    }

    /**
     * 统一执行 FFmpeg 命令(含超时控制)
     *
     * @param command 命令及参数列表
     * @return FFmpegResult 执行结果
     */
    public FFmpegResult execute(List<String> command) throws IOException, InterruptedException {
        log.info("执行 FFmpeg 命令: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 读取输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            String msg = "FFmpeg 命令执行超时(" + timeoutSeconds + "秒): " + String.join(" ", command);
            log.error(msg);
            throw new RuntimeException(msg);
        }

        int exitCode = process.exitValue();
        FFmpegResult result = new FFmpegResult(exitCode, output.toString());

        if (exitCode != 0) {
            String msg = "FFmpeg 命令执行失败(exitCode=" + exitCode + "): " + String.join(" ", command)
                    + "\n输出: " + output;
            log.error(msg);
            throw new RuntimeException(msg);
        }

        log.info("FFmpeg 命令执行成功");
        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据分辨率获取 scale 滤镜
     */
    private String getScaleFilter(String resolution) {
        if (resolution == null) {
            return null;
        }
        switch (resolution.toLowerCase()) {
            case "720p":
                return "scale=1280:720";
            case "1080p":
                return "scale=1920:1080";
            case "4k":
                return "scale=3840:2160";
            default:
                return null;
        }
    }

    /**
     * 根据位置获取 overlay 参数
     */
    private String getOverlayPosition(String position) {
        if (position == null) {
            return "main_w-overlay_w-10:main_h-overlay_h-10";
        }
        int margin = 10;
        switch (position.toUpperCase()) {
            case "TOP_LEFT":
                return margin + ":" + margin;
            case "TOP_RIGHT":
                return "main_w-overlay_w-" + margin + ":" + margin;
            case "BOTTOM_LEFT":
                return margin + ":main_h-overlay_h-" + margin;
            case "BOTTOM_RIGHT":
            default:
                return "main_w-overlay_w-" + margin + ":main_h-overlay_h-" + margin;
        }
    }

    /**
     * 构建 drawtext 滤镜
     */
    private String buildDrawtextFilter(WatermarkConfig config) {
        StringBuilder filter = new StringBuilder("drawtext=");
        filter.append("text='").append(escapeDrawtext(config.getContent())).append("'");
        filter.append(":fontsize=").append(config.getFontSize());
        filter.append(":fontcolor=").append(config.getFontColor());

        // 位置
        String position = config.getPosition();
        int margin = 10;
        if (position != null) {
            switch (position.toUpperCase()) {
                case "TOP_LEFT":
                    filter.append(":x=").append(margin).append(":y=").append(margin);
                    break;
                case "TOP_RIGHT":
                    filter.append(":x=w-tw-").append(margin).append(":y=").append(margin);
                    break;
                case "BOTTOM_LEFT":
                    filter.append(":x=").append(margin).append(":y=h-th-").append(margin);
                    break;
                case "BOTTOM_RIGHT":
                default:
                    filter.append(":x=w-tw-").append(margin).append(":y=h-th-").append(margin);
                    break;
            }
        } else {
            filter.append(":x=w-tw-").append(margin).append(":y=h-th-").append(margin);
        }

        // 透明度
        if (config.getOpacity() != null && config.getOpacity() < 1.0) {
            filter.append(":alpha=").append(config.getOpacity());
        }

        return filter.toString();
    }

    /**
     * 转义 drawtext 中的特殊字符
     */
    private String escapeDrawtext(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\\\\\")
                .replace("'", "\\\\'")
                .replace(":", "\\\\:")
                .replace("-", "\\-")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    // ==================== 图片处理 ====================

    /**
     * 创建 N×N 网格图片拼接
     *
     * @param inputImages 输入图片 URL 列表（最多 9 张）
     * @param outputPath 输出文件路径
     * @param gridSize 网格大小（如 3 表示 3×3）
     * @return 输出文件路径
     */
    public String createImageGrid(List<String> inputImages, String outputPath, int gridSize) {
        if (inputImages == null || inputImages.isEmpty()) {
            throw new IllegalArgumentException("输入图片列表为空");
        }

        if (inputImages.size() > gridSize * gridSize) {
            log.warn("图片数量 {} 超过网格容量 {}×{}，仅使用前 {} 张", inputImages.size(), gridSize, gridSize, gridSize * gridSize);
            inputImages = inputImages.subList(0, gridSize * gridSize);
        }

        log.info("开始创建 {}×{} 网格图片：图片数量={}", gridSize, gridSize, inputImages.size());

        try {
            // 计算需要的空白图片数量
            int totalSlots = gridSize * gridSize;
            int missingImages = totalSlots - inputImages.size();

            // 构建 FFmpeg 命令
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-y"); // 覆盖输出

            // 添加输入图片
            for (String imageUrl : inputImages) {
                command.add("-i");
                command.add(imageUrl);
            }

            // 添加空白图片占位
            for (int i = 0; i < missingImages; i++) {
                command.add("-f");
                command.add("lavfi");
                command.add("-i");
                command.add("color=c=black:s=1920x1080:d=1"); // 黑色占位图
            }

            // 构建 xstack 滤镜
            StringBuilder filterComplex = new StringBuilder();
            for (int i = 0; i < totalSlots; i++) {
                if (i > 0) {
                    filterComplex.append(";");
                }
                filterComplex.append("[").append(i).append(":0]scale=640:360[f").append(i).append("]");
            }

            // xstack 布局
            filterComplex.append("[");
            for (int i = 0; i < totalSlots; i++) {
                if (i > 0) {
                    filterComplex.append("][");
                }
                filterComplex.append("f").append(i);
            }
            filterComplex.append("]xstack=");

            // 计算每个位置
            int cellWidth = 640;
            int cellHeight = 360;
            int gap = 10; // 间隙
            List<String> layouts = new ArrayList<>();
            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    int x = col * (cellWidth + gap);
                    int y = row * (cellHeight + gap);
                    layouts.add(x + "_" + y);
                }
            }
            filterComplex.append("layout=").append(String.join("|", layouts));

            command.add("-filter_complex");
            command.add(filterComplex.toString());

            // 输出参数
            command.add("-q:v");
            command.add("2"); // 高质量
            command.add(outputPath);

            log.info("FFmpeg 命令：{}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("FFmpeg 执行超时");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("FFmpeg 输出：{}", output.toString());
                throw new RuntimeException("FFmpeg 执行失败，退出码：" + exitCode);
            }

            log.info("网格图片创建完成：{}", outputPath);
            return outputPath;

        } catch (IOException e) {
            log.error("FFmpeg 执行失败", e);
            throw new RuntimeException("FFmpeg 执行失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("FFmpeg 执行被中断", e);
        }
    }
}
                .replace("%", "\\\\%");
    }
}
