package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 图片处理控制器
 */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * POST /api/v1/images/episodes/{id}/grid - 生成剧集首帧图网格
     */
    @PostMapping("/episodes/{id}/grid")
    public ApiResponse<GridResponse> createEpisodeGrid(
            @PathVariable Long id,
            @RequestParam(defaultValue = "3") int gridSize
    ) {
        String imageUrl = imageService.createEpisodeFirstFrameGrid(id, gridSize);
        return ApiResponse.success(new GridResponse(id, imageUrl, "GRID"));
    }

    /**
     * POST /api/v1/images/projects/{id}/grid - 生成项目首帧图网格
     */
    @PostMapping("/projects/{id}/grid")
    public ApiResponse<GridResponse> createProjectGrid(
            @PathVariable Long id,
            @RequestParam(defaultValue = "3") int gridSize
    ) {
        String imageUrl = imageService.createProjectFirstFrameGrid(id, gridSize);
        return ApiResponse.success(new GridResponse(id, imageUrl, "PROJECT_GRID"));
    }

    /**
     * 网格响应 DTO
     */
    public record GridResponse(Long id, String imageUrl, String type) {
    }
}
