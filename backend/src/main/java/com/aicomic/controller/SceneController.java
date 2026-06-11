package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.SceneRequest;
import com.aicomic.entity.ExtractedAsset;
import com.aicomic.entity.Scene;
import com.aicomic.service.SceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 🌄 场景模块 REST API - 对应设计文档 6.4 场景模块端点
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SceneController {

    private final SceneService sceneService;

    /** GET /api/v1/projects/{projectId}/scenes - 获取项目场景列表 */
    @GetMapping("/projects/{projectId}/scenes")
    public ApiResponse<List<Scene>> listScenes(@PathVariable Long projectId) {
        return ApiResponse.success(sceneService.getScenesByProject(projectId));
    }

    /** POST /api/v1/projects/{projectId}/scenes - 手动创建场景 */
    @PostMapping("/projects/{projectId}/scenes")
    public ApiResponse<Scene> createScene(@PathVariable Long projectId, @RequestBody SceneRequest req) {
        Scene scene = new Scene();
        scene.setProjectId(projectId);
        scene.setName(req.getName());
        if (req.getDescription() != null) scene.setDescription(req.getDescription());
        if (req.getTimeOfDay() != null) scene.setTimeOfDay(Scene.TimeOfDay.valueOf(req.getTimeOfDay()));
        if (req.getWeather() != null) scene.setWeather(Scene.Weather.valueOf(req.getWeather()));
        if (req.getStyleHint() != null) scene.setStyleHint(req.getStyleHint());
        return ApiResponse.success(sceneService.saveScene(scene));
    }

    /** PUT /api/v1/scenes/{id} - 编辑场景 */
    @PutMapping("/scenes/{id}")
    public ApiResponse<Scene> updateScene(@PathVariable Long id, @RequestBody SceneRequest req) {
        Scene scene = sceneService.findSceneById(id);
        if (req.getName() != null) scene.setName(req.getName());
        if (req.getDescription() != null) scene.setDescription(req.getDescription());
        if (req.getTimeOfDay() != null) scene.setTimeOfDay(Scene.TimeOfDay.valueOf(req.getTimeOfDay()));
        if (req.getWeather() != null) scene.setWeather(Scene.Weather.valueOf(req.getWeather()));
        if (req.getStyleHint() != null) scene.setStyleHint(req.getStyleHint());
        return ApiResponse.success(sceneService.saveScene(scene));
    }

    /** DELETE /api/v1/scenes/{id} - 删除场景 */
    @DeleteMapping("/scenes/{id}")
    public ApiResponse<Void> deleteScene(@PathVariable Long id) {
        sceneService.deleteScene(id);
        return ApiResponse.success();
    }

    // ==================== AI 提取 & 四视图 ====================

    /** POST /api/v1/scripts/{id}/extract-scenes - AI 从剧本提取场景 */
    @PostMapping("/scripts/{id}/extract-scenes")
    public ApiResponse<Void> extractScenes(@PathVariable Long id) {
        sceneService.extractScenesAsync(null, id);
        return ApiResponse.success();
    }

    /** GET /api/v1/scenes/extracted-assets - 查看待确认的场景资产 */
    @GetMapping("/scenes/extracted-assets")
    public ApiResponse<List<ExtractedAsset>> listExtractedSceneAssets(@RequestParam Long projectId) {
        return ApiResponse.success(sceneService.getExtractedSceneAssets(projectId));
    }

    /** PUT /api/v1/extracted-assets/{id}/confirm-scene - 确认场景资产入库 */
    @PutMapping("/extracted-assets/{id}/confirm-scene")
    public ApiResponse<Scene> confirmSceneAsset(@PathVariable Long id) {
        return ApiResponse.success(sceneService.confirmExtractedSceneAsset(id));
    }

    /** POST /api/v1/scenes/{id}/generate-quad-view - 生成场景四视图 */
    @PostMapping("/scenes/{id}/generate-quad-view")
    public ApiResponse<Void> generateQuadView(@PathVariable Long id) {
        sceneService.generateQuadViewAsync(id);
        return ApiResponse.success();
    }

    /** POST /api/v1/scenes/{id}/regenerate-view/{viewType} - 重试单个视角 */
    @PostMapping("/scenes/{id}/regenerate-view/{viewType}")
    public ApiResponse<Void> regenerateView(@PathVariable Long id, @PathVariable String viewType) {
        sceneService.regenerateSingleViewAsync(id, viewType);
        return ApiResponse.success();
    }
}
