package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.CompositeRequest;
import com.aicomic.dto.ExportConfig;
import com.aicomic.dto.WatermarkConfig;
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

    /** POST /api/v1/episodes/{id}/compose - 成片合成（FFmpeg拼接+字幕+音频+转场） */
    @PostMapping("/episodes/{id}/compose")
    public ApiResponse<Void> compositeFinalVideo(@PathVariable Long id,
                                                  @RequestBody(required = false) CompositeRequest request) {
        if (request == null) {
            request = new CompositeRequest();
            request.setEpisodeId(id);
        }
        sLevelService.compositeFinalVideoAsync(id, request);
        return ApiResponse.success();
    }

    /** POST /api/v1/video/export - 导出视频 */
    @PostMapping("/video/export")
    public ApiResponse<Void> exportVideo(@RequestBody ExportRequest req) {
        ExportConfig config = new ExportConfig();
        config.setFormat(req.getFormat());
        config.setResolution(req.getResolution());
        config.setBitrate(req.getBitrate());
        config.setFps(req.getFps());

        sLevelService.exportVideoAsync(req.getProjectId(), req.getEpisodeId(), config);
        return ApiResponse.success();
    }

    /** POST /api/v1/video/watermark - 添加水印 */
    @PostMapping("/video/watermark")
    public ApiResponse<Void> addWatermark(@RequestBody WatermarkRequest req) {
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

        sLevelService.addWatermarkAsync(req.getProjectId(), req.getEpisodeId(), config);
        return ApiResponse.success();
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
