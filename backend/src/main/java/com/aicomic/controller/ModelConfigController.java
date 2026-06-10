package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.ModelConfig;
import com.aicomic.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ApiResponse<ModelConfig> create(@RequestBody ModelConfig config) {
        return ApiResponse.success(modelConfigService.createConfig(config));
    }

    /** PUT /api/v1/model-configs/{id} - 更新模型配置 */
    @PutMapping("/{id}")
    public ApiResponse<ModelConfig> update(@PathVariable Long id, @RequestBody ModelConfig config) {
        return ApiResponse.success(modelConfigService.updateConfig(id, config));
    }

    /** DELETE /api/v1/model-configs/{id} - 删除模型配置 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        modelConfigService.deleteConfig(id);
        return ApiResponse.success();
    }
}
