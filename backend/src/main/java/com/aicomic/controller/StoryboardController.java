package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.StoryboardRequest;
import com.aicomic.entity.Storyboard;
import com.aicomic.service.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 🎬 分镜模块 REST API - 对应设计文档 6.4 分镜模块端点
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StoryboardController {

    private final StoryboardService storyboardService;

    /** GET /api/v1/episodes/{episodeId}/storyboards - 获取剧集分镜列表 */
    @GetMapping("/episodes/{episodeId}/storyboards")
    public ApiResponse<List<Storyboard>> listStoryboards(@PathVariable Long episodeId) {
        return ApiResponse.success(storyboardService.getStoryboardsByEpisode(episodeId));
    }

    /** POST /api/v1/episodes/{episodeId}/storyboards - 手动创建分镜 */
    @PostMapping("/episodes/{episodeId}/storyboards")
    public ApiResponse<Storyboard> createStoryboard(@PathVariable Long episodeId, @RequestBody StoryboardRequest req) {
        Storyboard sb = new Storyboard();
        sb.setEpisodeId(episodeId);
        storyboardService.applyRequestToStoryboard(req, sb);
        return ApiResponse.success(storyboardService.saveStoryboard(sb));
    }

    /** PUT /api/v1/storyboards/{id} - 编辑分镜 */
    @PutMapping("/storyboards/{id}")
    public ApiResponse<Storyboard> updateStoryboard(@PathVariable Long id, @RequestBody StoryboardRequest req) {
        Storyboard sb = storyboardService.findStoryboardById(id);
        storyboardService.applyRequestToStoryboard(req, sb);
        return ApiResponse.success(storyboardService.saveStoryboard(sb));
    }

    /** DELETE /api/v1/storyboards/{id} - 删除分镜 */
    @DeleteMapping("/storyboards/{id}")
    public ApiResponse<Void> deleteStoryboard(@PathVariable Long id) {
        storyboardService.deleteStoryboard(id);
        return ApiResponse.success();
    }

    // ==================== 三步流程 (ADR-19) ====================

    /** POST /api/v1/episodes/{id}/parse - 步骤1：AI解析剧本为结构化分镜数据 */
    @PostMapping("/episodes/{id}/parse")
    public ApiResponse<Void> parseScriptToStoryboard(@PathVariable Long id) {
        storyboardService.parseScriptToStoryboardAsync(id);
        return ApiResponse.success();
    }

    /** POST /api/v1/storyboards/batch-update - 步骤2：批量编辑分镜 */
    @PostMapping("/storyboards/batch-update")
    public ApiResponse<List<Storyboard>> batchUpdateStoryboards(@RequestBody List<StoryboardRequest> requests) {
        return ApiResponse.success(storyboardService.batchUpdateStoryboards(requests));
    }

    /** POST /api/v1/episodes/{episodeId}/generate-images - 步骤3：批量生成分镜图 */
    @PostMapping("/episodes/{episodeId}/generate-images")
    public ApiResponse<Void> generateStoryboardImages(@PathVariable Long episodeId) {
        storyboardService.generateStoryboardImagesAsync(episodeId);
        return ApiResponse.success();
    }

    /** POST /api/v1/storyboards/{id}/generate-image - 单分镜重新生成图片 */
    @PostMapping("/storyboards/{id}/generate-image")
    public ApiResponse<Void> regenerateStoryboardImage(@PathVariable Long id) {
        storyboardService.generateSingleStoryboardImageAsync(id);
        return ApiResponse.success();
    }

    /** POST /api/v1/storyboards/{id}/resolve-references - 自动解析角色/场景引用（名称→ID） */
    @PostMapping("/storyboards/{id}/resolve-references")
    public ApiResponse<Storyboard> resolveReferences(@PathVariable Long id) {
        return ApiResponse.success(storyboardService.resolveReferences(id));
    }
}