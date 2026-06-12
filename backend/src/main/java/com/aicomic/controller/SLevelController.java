package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.CompositeRequest;
import com.aicomic.dto.ExportConfig;
import com.aicomic.dto.WatermarkConfig;
import com.aicomic.entity.Episode;
import com.aicomic.entity.Script;
import com.aicomic.repository.EpisodeRepository;
import com.aicomic.repository.ScriptRepository;
import com.aicomic.service.SLevelService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * S级模块 REST API - 对应设计文档 6.4 S级/输出模块端点
 * 负责：成片合成、字幕叠加、音画同步、视频导出（MP4/MOV）、水印添加
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SLevelController {

    private final SLevelService sLevelService;
    private final EpisodeRepository episodeRepository;
    private final ScriptRepository scriptRepository;

    /** POST /api/v1/episodes/{id}/compose - 成片合成（FFmpeg拼接+字幕+音频+转场） */
    @PostMapping("/episodes/{id}/compose")
    public ApiResponse<Void> compositeFinalVideo(@PathVariable Long id,
                                                  @RequestBody(required = false) CompositeRequest request) {
        Episode episode = episodeRepository.findById(id)
                .orElseThrow(() -> new com.aicomic.common.exception.ResourceNotFoundException("剧集不存在: " + id));
        Long projectId = resolveProjectId(episode);

        if (request == null) {
            request = new CompositeRequest();
        }
        request.setEpisodeId(id);
        sLevelService.compositeFinalVideoAsync(projectId, request);
        return ApiResponse.success();
    }

    /** POST /api/v1/episodes/{id}/export - 导出视频 */
    @PostMapping("/episodes/{id}/export")
    public ApiResponse<Void> exportVideo(@PathVariable Long id, @RequestBody ExportRequest req) {
        Episode episode = episodeRepository.findById(id)
                .orElseThrow(() -> new com.aicomic.common.exception.ResourceNotFoundException("剧集不存在: " + id));
        Long projectId = req.getProjectId() != null ? req.getProjectId() : resolveProjectId(episode);

        ExportConfig config = new ExportConfig();
        config.setFormat(req.getFormat());
        config.setResolution(req.getResolution());
        config.setBitrate(req.getBitrate());
        config.setFps(req.getFps());

        sLevelService.exportVideoAsync(projectId, id, config);
        return ApiResponse.success();
    }

    /** POST /api/v1/episodes/{id}/watermark - 添加水印 */
    @PostMapping("/episodes/{id}/watermark")
    public ApiResponse<Void> addWatermark(@PathVariable Long id, @RequestBody WatermarkRequest req) {
        Episode episode = episodeRepository.findById(id)
                .orElseThrow(() -> new com.aicomic.common.exception.ResourceNotFoundException("剧集不存在: " + id));
        Long projectId = req.getProjectId() != null ? req.getProjectId() : resolveProjectId(episode);

        WatermarkConfig config = new WatermarkConfig();
        config.setType(req.getWatermarkType());
        config.setContent(req.getWatermarkContent());
        if (req.getImagePath() != null) {
            config.setImagePath(req.getImagePath());
        }
        if (req.getPosition() != null) {
            config.setPosition(req.getPosition());
        }
        if (req.getOpacity() != null) {
            config.setOpacity(req.getOpacity());
        }
        if (req.getFontSize() != null) {
            config.setFontSize(req.getFontSize());
        }
        if (req.getFontColor() != null) {
            config.setFontColor(req.getFontColor());
        }

        sLevelService.addWatermarkAsync(projectId, id, config);
        return ApiResponse.success();
    }

    /** 通过 Episode 反查 projectId */
    private Long resolveProjectId(Episode episode) {
        if (episode.getScriptId() != null) {
            Script script = scriptRepository.findById(episode.getScriptId())
                    .orElseThrow(() -> new com.aicomic.common.exception.ResourceNotFoundException("剧本不存在: " + episode.getScriptId()));
            return script.getProjectId();
        }
        throw new com.aicomic.common.exception.ResourceNotFoundException("无法确定剧集所属项目");
    }

    @Data
    public static class ExportRequest {
        private Long projectId;
        private Long episodeId;
        private String format = "mp4";
        private String resolution = "1080p";
        private Integer bitrate;
        private Integer fps;
    }

    @Data
    public static class WatermarkRequest {
        private Long projectId;
        private Long episodeId;
        private String watermarkType = "TEXT";
        private String watermarkContent;
        private String imagePath;
        private String position;
        private Double opacity;
        private Integer fontSize;
        private String fontColor;
    }
}
