package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.AssetItem;
import com.aicomic.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 素材库 REST API - 上传/查询/删除/角色场景关联
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /** GET /api/v1/assets?projectId=X&type=IMAGE&tags=xxx — 列表查询 */
    @GetMapping("/assets")
    public ApiResponse<List<AssetItem>> listAssets(
            @RequestParam Long projectId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String tags) {
        return ApiResponse.success(assetService.getAssetsByProject(projectId, type, tags));
    }

    /** GET /api/v1/assets/{id} — 详情 */
    @GetMapping("/assets/{id}")
    public ApiResponse<AssetItem> getAsset(@PathVariable Long id) {
        return ApiResponse.success(assetService.getAsset(id));
    }

    /** POST /api/v1/projects/{projectId}/assets/upload — 上传(multipart/form-data) */
    @PostMapping("/projects/{projectId}/assets/upload")
    public ApiResponse<AssetItem> uploadAsset(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) String tags) {
        return ApiResponse.success(assetService.uploadAsset(projectId, file, tags));
    }

    /** DELETE /api/v1/assets/{id} — 删除 */
    @DeleteMapping("/assets/{id}")
    public ApiResponse<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ApiResponse.success();
    }

    /** PUT /api/v1/assets/{id}/link-character?characterId=X — 关联角色 */
    @PutMapping("/assets/{id}/link-character")
    public ApiResponse<AssetItem> linkToCharacter(
            @PathVariable Long id,
            @RequestParam Long characterId) {
        return ApiResponse.success(assetService.linkToCharacter(id, characterId));
    }

    /** PUT /api/v1/assets/{id}/link-scene?sceneId=X — 关联场景 */
    @PutMapping("/assets/{id}/link-scene")
    public ApiResponse<AssetItem> linkToScene(
            @PathVariable Long id,
            @RequestParam Long sceneId) {
        return ApiResponse.success(assetService.linkToScene(id, sceneId));
    }
}
