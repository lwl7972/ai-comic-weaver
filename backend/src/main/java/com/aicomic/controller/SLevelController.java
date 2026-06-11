package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.service.SLevelService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * ⭐ S级模块 REST API - 对应设计文档 6.4 S级/输出模块端点
 * 负责：成片合成、字幕叠加、音画同步、视频导出（MP4/MOV）、水印添加
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SLevelController {

    private final SLevelService sLevelService;

    /** POST /api/v1/episodes/{id}/compose - 成片合成（FFmpeg拼接+字幕+音频+转场） */
    @PostMapping("/episodes/{id}/compose")
    public ApiResponse<Void> compositeFinalVideo(@PathVariable Long id) {
        // episodeId maps to projectId for S-level operations
        sLevelService.compositeFinalVideoAsync(id);
        return ApiResponse.success();
    }

    /** POST /api/v1/video/export - 导出视频 */
    @PostMapping("/video/export")
    public ApiResponse<Void> exportVideo(@RequestBody ExportRequest req) {
        sLevelService.exportVideoAsync(req.getProjectId(), req.getFormat(),
                req.getResolution(), req.getBitrate(), req.getFps());
        return ApiResponse.success();
    }

    /** POST /api/v1/video/watermark - 添加水印 */
    @PostMapping("/video/watermark")
    public ApiResponse<Void> addWatermark(@RequestBody WatermarkRequest req) {
        sLevelService.addWatermarkAsync(req.getProjectId(), req.getWatermarkType(), req.getWatermarkContent());
        return ApiResponse.success();
    }

    @Data
    public static class ExportRequest {
        private Long projectId;
        private String format = "MP4";
        private int resolution = 1080;
        private int bitrate = 8000;
        private int fps = 24;
    }

    @Data
    public static class WatermarkRequest {
        private Long projectId;
        private String watermarkType = "TEXT";
        private String watermarkContent;
    }
}
