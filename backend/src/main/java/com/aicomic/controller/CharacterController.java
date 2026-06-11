package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.CharacterRequest;
import com.aicomic.entity.Character;
import com.aicomic.entity.ExtractedAsset;
import com.aicomic.service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色模块 REST API - 对应 6.4.3 角色模块端点
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    /** POST /api/v1/projects/{projectId}/characters - 手动创建角色 */
    @PostMapping("/projects/{projectId}/characters")
    public ApiResponse<Character> createCharacter(@PathVariable Long projectId, @RequestBody CharacterRequest req) {
        Character character = new Character();
        character.setProjectId(projectId);
        character.setName(req.getName());
        if (req.getRole() != null) {
            character.setRole(Character.CharacterRole.valueOf(req.getRole()));
        }
        if (req.getGender() != null) {
            character.setGender(Character.Gender.valueOf(req.getGender()));
        }
        if (req.getAgeRange() != null) character.setAgeRange(req.getAgeRange());
        if (req.getAppearance() != null) character.setAppearance(req.getAppearance());
        if (req.getPersonality() != null) character.setPersonality(req.getPersonality());
        if (req.getAnchorPrompt() != null) character.setAnchorPrompt(req.getAnchorPrompt());
        if (req.getReferenceImageId() != null) character.setReferenceImageId(req.getReferenceImageId());
        return ApiResponse.success(characterService.saveCharacter(character));
    }

    /** GET /api/v1/projects/{projectId}/characters - 获取项目角色列表 */
    @GetMapping("/projects/{projectId}/characters")
    public ApiResponse<List<Character>> listCharacters(@PathVariable Long projectId) {
        return ApiResponse.success(characterService.getCharactersByProject(projectId));
    }

    /** GET /api/v1/characters/{id} - 角色详情(6层锚点) */
    @GetMapping("/characters/{id}")
    public ApiResponse<Character> getCharacter(@PathVariable Long id) {
        return characterService.getCharacter(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "角色不存在"));
    }

    /** PUT /api/v1/characters/{id} - 编辑角色 */
    @PutMapping("/characters/{id}")
    public ApiResponse<Character> updateCharacter(@PathVariable Long id, @RequestBody CharacterRequest req) {
        return characterService.getCharacter(id)
                .map(existing -> {
                    if (req.getName() != null) existing.setName(req.getName());
                    if (req.getRole() != null) {
                        existing.setRole(Character.CharacterRole.valueOf(req.getRole()));
                    }
                    if (req.getGender() != null) {
                        existing.setGender(Character.Gender.valueOf(req.getGender()));
                    }
                    if (req.getAgeRange() != null) existing.setAgeRange(req.getAgeRange());
                    if (req.getAppearance() != null) existing.setAppearance(req.getAppearance());
                    if (req.getPersonality() != null) existing.setPersonality(req.getPersonality());
                    if (req.getAnchorPrompt() != null) existing.setAnchorPrompt(req.getAnchorPrompt());
                    if (req.getReferenceImageId() != null) existing.setReferenceImageId(req.getReferenceImageId());
                    return ApiResponse.success(characterService.saveCharacter(existing));
                })
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "角色不存在"));
    }

    /** DELETE /api/v1/characters/{id} - 删除角色 */
    @DeleteMapping("/characters/{id}")
    public ApiResponse<Void> deleteCharacter(@PathVariable Long id) {
        characterService.deleteCharacter(id);
        return ApiResponse.success();
    }

    // ==================== AI 提取 & 定妆图 ====================

    /** POST /api/v1/scripts/{id}/extract-assets - AI 提取资产(触发 A.6) */
    @PostMapping("/scripts/{id}/extract-assets")
    public ApiResponse<Void> extractAssets(
            @PathVariable Long id,
            @RequestParam(value = "assetType", defaultValue = "CHARACTER") String assetType) {
        // 传递 assetType 参数到 Service，当前仅支持 CHARACTER
        characterService.extractCharactersAsync(null, id);
        return ApiResponse.success();
    }

    /** GET /api/v1/extracted-assets - 待确认资产列表 */
    @GetMapping("/extracted-assets")
    public ApiResponse<List<ExtractedAsset>> listExtractedAssets(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "CHARACTER") String type) {
        return ApiResponse.success(characterService.getExtractedAssets(projectId, type));
    }

    /** PUT /api/v1/extracted-assets/{id}/confirm - 确认入库资产 */
    @PutMapping("/extracted-assets/{id}/confirm")
    public ApiResponse<Character> confirmAsset(@PathVariable Long id) {
        return ApiResponse.success(characterService.confirmExtractedAsset(id));
    }

    /** POST /api/v1/characters/{id}/makeup - 生成定妆图 */
    @PostMapping("/characters/{id}/makeup")
    public ApiResponse<Void> generateMakeupImage(@PathVariable Long id) {
        characterService.generateMakeupImageAsync(id);
        return ApiResponse.success();
    }
}
