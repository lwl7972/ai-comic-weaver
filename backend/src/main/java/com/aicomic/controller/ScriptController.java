package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.EpisodeRequest;
import com.aicomic.dto.ScriptRequest;
import com.aicomic.entity.Episode;
import com.aicomic.entity.Script;
import com.aicomic.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 剧本模块 REST API - 对应 6.4.3 剧本模块端点
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    // ==================== 剧本 CRUD ====================

    /** POST /api/v1/projects/{projectId}/scripts - 创建剧本 */
    @PostMapping("/projects/{projectId}/scripts")
    public ApiResponse<Script> createScript(@PathVariable Long projectId, @RequestBody ScriptRequest req) {
        Script script = new Script();
        script.setProjectId(projectId);
        script.setTitle(req.getTitle());
        return ApiResponse.success(scriptService.saveScript(script));
    }

    /** GET /api/v1/projects/{projectId}/scripts - 获取项目的所有剧本 */
    @GetMapping("/projects/{projectId}/scripts")
    public ApiResponse<List<Script>> listScripts(@PathVariable Long projectId) {
        return ApiResponse.success(scriptService.getScriptsByProject(projectId));
    }

    /** GET /api/v1/scripts/{id} - 获取剧本 */
    @GetMapping("/scripts/{id}")
    public ApiResponse<Script> getScript(@PathVariable Long id) {
        return scriptService.getScript(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "剧本不存在"));
    }

    /** PUT /api/v1/scripts/{id} - 更新剧本内容 */
    @PutMapping("/scripts/{id}")
    public ApiResponse<Script> updateScript(@PathVariable Long id, @RequestBody ScriptRequest req) {
        return scriptService.getScript(id)
                .map(existing -> {
                    if (req.getTitle() != null) existing.setTitle(req.getTitle());
                    if (req.getOutline() != null) existing.setOutline(req.getOutline());
                    if (req.getCurrentStep() != null) {
                        existing.setCurrentStep(Script.ScriptStep.valueOf(req.getCurrentStep()));
                    }
                    if (req.getTotalEpisodes() != null) existing.setTotalEpisodes(req.getTotalEpisodes());
                    if (req.getStatus() != null) {
                        existing.setStatus(Script.ScriptStatus.valueOf(req.getStatus()));
                    }
                    return ApiResponse.success(scriptService.saveScript(existing));
                })
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "剧本不存在"));
    }

    /** DELETE /api/v1/scripts/{id} - 删除剧本 */
    @DeleteMapping("/scripts/{id}")
    public ApiResponse<Void> deleteScript(@PathVariable Long id) {
        scriptService.deleteScript(id);
        return ApiResponse.success();
    }

    // ==================== 大纲生成 ====================

    /** POST /api/v1/scripts/{id}/outline - AI 生成大纲 (A.5) */
    @PostMapping("/scripts/{id}/outline")
    public ApiResponse<Void> generateOutline(@PathVariable Long id) {
        Script script = scriptService.getScript(id)
                .orElseThrow(() -> new IllegalArgumentException("剧本不存在"));
        scriptService.generateOutlineAsync(script.getProjectId(), id);
        return ApiResponse.success();
    }

    // ==================== 剧集管理 ====================

    /** GET /api/v1/scripts/{id}/episodes - 获取剧集列表 */
    @GetMapping("/scripts/{id}/episodes")
    public ApiResponse<List<Episode>> listEpisodes(@PathVariable Long id) {
        return ApiResponse.success(scriptService.getEpisodesByScript(id));
    }

    /** POST /api/v1/scripts/{id}/episodes - 创建剧集 */
    @PostMapping("/scripts/{id}/episodes")
    public ApiResponse<Episode> createEpisode(@PathVariable Long id, @RequestBody EpisodeRequest req) {
        Episode episode = new Episode();
        episode.setScriptId(id);
        episode.setTitle(req.getTitle());
        if (req.getEpisodeNumber() != null) episode.setEpisodeNumber(req.getEpisodeNumber());
        return ApiResponse.success(scriptService.saveEpisode(episode));
    }

    /** PUT /api/v1/episodes/{id} - 更新剧集 */
    @PutMapping("/episodes/{id}")
    public ApiResponse<Episode> updateEpisode(@PathVariable Long id, @RequestBody EpisodeRequest req) {
        return scriptService.getEpisode(id)
                .map(existing -> {
                    if (req.getTitle() != null) existing.setTitle(req.getTitle());
                    if (req.getEpisodeNumber() != null) existing.setEpisodeNumber(req.getEpisodeNumber());
                    if (req.getScriptContent() != null) existing.setScriptContent(req.getScriptContent());
                    if (req.getParsedData() != null) existing.setParsedData(req.getParsedData());
                    if (req.getStatus() != null) {
                        existing.setStatus(Episode.EpisodeStatus.valueOf(req.getStatus()));
                    }
                    return ApiResponse.success(scriptService.saveEpisode(existing));
                })
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "剧集不存在"));
    }

    /** GET /api/v1/episodes/{id} - 获取单个剧集 */
    @GetMapping("/episodes/{id}")
    public ApiResponse<Episode> getEpisode(@PathVariable Long id) {
        return scriptService.getEpisode(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "剧集不存在"));
    }

    /** POST /api/v1/episodes/{id}/generate-script - AI 生成剧集剧本 */
    @PostMapping("/episodes/{id}/generate-script")
    public ApiResponse<Void> generateEpisodeScript(@PathVariable Long id) {
        Episode episode = scriptService.getEpisode(id)
                .orElseThrow(() -> new IllegalArgumentException("剧集不存在"));
        scriptService.generateEpisodeScriptAsync(episode.getScriptId(), id);
        return ApiResponse.success();
    }
}
