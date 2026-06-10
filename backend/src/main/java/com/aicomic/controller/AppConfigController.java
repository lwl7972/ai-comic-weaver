package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.entity.AppConfig;
import com.aicomic.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 应用配置 REST API - 配置中心
 * 对应 6.4.3 配置中心端点 + 品牌资源配置
 */
@RestController
@RequestMapping("/api/v1/app-config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigRepository appConfigRepository;

    /** GET /api/v1/app-config - 获取所有全局配置 (Map格式) */
    @GetMapping
    public ApiResponse<Map<String, String>> getAllConfig() {
        List<AppConfig> configs = appConfigRepository.findAll();
        Map<String, String> map = configs.stream()
                .collect(java.util.stream.Collectors.toMap(AppConfig::getKey, AppConfig::getValue));
        return ApiResponse.success(map);
    }

    /** PUT /api/v1/app-config - 批量更新配置 */
    @PutMapping
    @Transactional
    public ApiResponse<Void> updateConfig(@RequestBody Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            AppConfig config = appConfigRepository.findByKey(entry.getKey())
                    .orElse(new AppConfig());
            config.setKey(entry.getKey());
            config.setValue(entry.getValue());
            appConfigRepository.save(config);
        }
        return ApiResponse.success();
    }

    /** GET /api/v1/app-config/{key} - 获取单个配置项 */
    @GetMapping("/{key}")
    public ApiResponse<String> getConfig(@PathVariable String key) {
        return appConfigRepository.findByKey(key)
                .map(c -> ApiResponse.success(c.getValue()))
                .orElseGet(() -> ApiResponse.error(ApiResponse.CONFIG_NOT_FOUND, "配置项不存在: " + key));
    }
}
