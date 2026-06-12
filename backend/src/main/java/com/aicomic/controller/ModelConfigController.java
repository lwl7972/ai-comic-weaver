package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.dto.ModelConfigRequest;
import com.aicomic.entity.ModelConfig;
import com.aicomic.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型配置管理 API（ADR-6：模型无关抽象层）
 */
@RestController
@RequestMapping("/api/v1/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    /** GET /api/v1/model-configs - 获取所有模型配置 */
    @GetMapping
    public ApiResponse<List<ModelConfig>> list() {
        return ApiResponse.success(modelConfigService.getAllConfigs());
    }

    /** GET /api/v1/model-configs/active - 获取激活的模型配置 */
    @GetMapping("/active")
    public ApiResponse<List<ModelConfig>> active() {
        return ApiResponse.success(modelConfigService.getActiveConfigs());
    }

    /** GET /api/v1/model-configs?type=TEXT - 按类型筛选 */
    @GetMapping(params = "type")
    public ApiResponse<List<ModelConfig>> byType(@RequestParam ModelConfig.ModelType type) {
        return ApiResponse.success(modelConfigService.getConfigsByType(type));
    }

    /** GET /api/v1/model-configs/{id} - 获取单个配置 */
    @GetMapping("/{id}")
    public ApiResponse<ModelConfig> detail(@PathVariable Long id) {
        return modelConfigService.getConfig(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(ApiResponse.NOT_FOUND, "模型配置不存在"));
    }

    /** POST /api/v1/model-configs - 创建模型配置 */
    @PostMapping
    public ApiResponse<ModelConfig> create(@RequestBody ModelConfigRequest req) {
        ModelConfig config = new ModelConfig();
        applyRequestToConfig(config, req);
        return ApiResponse.success(modelConfigService.createConfig(config));
    }

    /** PUT /api/v1/model-configs/{id} - 更新模型配置 */
    @PutMapping("/{id}")
    public ApiResponse<ModelConfig> update(@PathVariable Long id, @RequestBody ModelConfigRequest req) {
        ModelConfig config = modelConfigService.getConfig(id)
                .orElseGet(ModelConfig::new);
        applyRequestToConfig(config, req);
        return ApiResponse.success(modelConfigService.updateConfig(id, config));
    }

    private void applyRequestToConfig(ModelConfig config, ModelConfigRequest req) {
        if (req.getName() != null) config.setName(req.getName());
        if (req.getProvider() != null) config.setProvider(req.getProvider());
        if (req.getType() != null) config.setType(req.getType());
        if (req.getApiUrl() != null) config.setApiUrl(req.getApiUrl());
        if (req.getApiKey() != null) config.setApiKey(req.getApiKey());
        if (req.getModelName() != null) config.setModelName(req.getModelName());
        if (req.getMaxTokens() != null) config.setMaxTokens(req.getMaxTokens());
        if (req.getIsActive() != null) config.setIsActive(req.getIsActive());
        if (req.getPriority() != null) config.setPriority(req.getPriority());
        if (req.getWorkflowId() != null) config.setWorkflowId(req.getWorkflowId());
        if (req.getBotId() != null) config.setBotId(req.getBotId());
        if (req.getAppId() != null) config.setAppId(req.getAppId());
        if (req.getIsCozeWorkflow() != null) config.setIsCozeWorkflow(req.getIsCozeWorkflow());
    }

    /** DELETE /api/v1/model-configs/{id} - 删除模型配置 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        modelConfigService.deleteConfig(id);
        return ApiResponse.success();
    }

    /** POST /api/v1/model-configs/{id}/test-connection - 测试模型连接 */
    @PostMapping("/{id}/test-connection")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable Long id) {
        return ApiResponse.success(modelConfigService.testConnection(id));
    }
}
