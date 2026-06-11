package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.ChapterSummary;
import com.aicomic.entity.Novel;
import com.aicomic.service.NovelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 小说导入模块 REST API - 对应 6.4.3 小说导入端点
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    /** POST /api/v1/projects/{projectId}/novels/upload - 上传小说文件 */
    @PostMapping("/projects/{projectId}/novels/upload")
    public ApiResponse<Novel> uploadNovel(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {

        if (file.isEmpty()) {
            return ApiResponse.error(ApiResponse.PARAM_ERROR, "文件不能为空");
        }

        // 文件大小校验：最大 50MB
        long maxSize = 50 * 1024 * 1024L;
        if (file.getSize() > maxSize) {
            return ApiResponse.error(ApiResponse.PARAM_ERROR, "文件大小不能超过50MB");
        }

        // 文件类型校验：仅允许纯文本
        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            String lowerName = originalName.toLowerCase();
            if (!lowerName.endsWith(".txt") && !lowerName.endsWith(".text")) {
                return ApiResponse.error(ApiResponse.PARAM_ERROR, "仅支持 .txt 纯文本文件");
            }
        }

        Novel novel = novelService.uploadAndParse(projectId, file, title);
        return ApiResponse.success(novel);
    }

    /** GET /api/v1/projects/{projectId}/novels - 获取项目的小说列表 */
    @GetMapping("/projects/{projectId}/novels")
    public ApiResponse<List<Novel>> listNovels(@PathVariable Long projectId) {
        return ApiResponse.success(novelService.getNovelsByProject(projectId));
    }

    /** GET /api/v1/novels/{id} - 获取小说详情 */
    @GetMapping("/novels/{id}")
    public ApiResponse<Novel> getNovel(@PathVariable Long id) {
        return novelService.getNovel(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "小说不存在"));
    }

    /** POST /api/v1/novels/{id}/summarize - 触发分章摘要 (A.8.2) */
    @PostMapping("/novels/{id}/summarize")
    public ApiResponse<Void> summarize(@PathVariable Long id) {
        novelService.summarizeChaptersAsync(id);
        return ApiResponse.success();
    }

    /** GET /api/v1/novels/{id}/summaries - 获取各章摘要进度 */
    @GetMapping("/novels/{id}/summaries")
    public ApiResponse<List<ChapterSummary>> getSummaries(@PathVariable Long id) {
        return ApiResponse.success(novelService.getSummariesByNovel(id));
    }

    /** POST /api/v1/novels/{id}/convert - 小说→剧本转换 (A.1) */
    @PostMapping("/novels/{id}/convert")
    public ApiResponse<Void> convertToScript(@PathVariable Long id) {
        novelService.convertToScriptAsync(id);
        return ApiResponse.success();
    }

    /** GET /api/v1/novels/{id}/status - 获取转换状态 */
    @GetMapping("/novels/{id}/status")
    public ApiResponse<Novel> getStatus(@PathVariable Long id) {
        return novelService.getNovel(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "小说不存在"));
    }
}
