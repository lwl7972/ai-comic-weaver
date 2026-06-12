package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.common.util.CryptoUtils;
import com.aicomic.entity.ModelConfig;
import com.aicomic.repository.ModelConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 模型配置管理服务
 * 管理所有外部 AI 模型 API 的连接参数（ADR-6：模型无关抽象层）
 * <p>
 * apiKey 字段使用 AES-256-CBC 加密存储，读取时自动解密。
 */
@Slf4j
@Service
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;
    private final RestTemplate aiRestTemplate;

    public ModelConfigService(ModelConfigRepository modelConfigRepository,
                              @Qualifier("aiRestTemplate") RestTemplate aiRestTemplate) {
        this.modelConfigRepository = modelConfigRepository;
        this.aiRestTemplate = aiRestTemplate;
    }

    /**
     * 获取所有激活的模型配置（按优先级排序，apiKey 脱敏）
     */
    public List<ModelConfig> getActiveConfigs() {
        return maskApiKeys(modelConfigRepository.findByIsActiveTrueOrderByPriorityAsc());
    }

    /**
     * 获取所有模型配置（apiKey 脱敏）
     */
    public List<ModelConfig> getAllConfigs() {
        return maskApiKeys(modelConfigRepository.findAll());
    }

    /**
     * 按类型获取模型配置（apiKey 脱敏）
     */
    public List<ModelConfig> getConfigsByType(ModelConfig.ModelType type) {
        return maskApiKeys(modelConfigRepository.findByType(type));
    }

    /**
     * 获取单个模型配置（apiKey 脱敏，仅展示用）
     */
    public Optional<ModelConfig> getConfig(Long id) {
        return modelConfigRepository.findById(id).map(this::maskApiKey);
    }

    /**
     * 获取解密后的模型配置（仅限后端内部调用外部 API 时使用）
     */
    public Optional<ModelConfig> getDecryptedConfig(Long id) {
        return modelConfigRepository.findById(id).map(this::decryptApiKey);
    }

    /**
     * 获取解密后的激活配置列表（仅限后端内部调用外部 API 时使用）
     */
    public List<ModelConfig> getDecryptedActiveConfigs() {
        return decryptApiKeys(modelConfigRepository.findByIsActiveTrueOrderByPriorityAsc());
    }

    /**
     * 创建模型配置（自动加密 apiKey）
     */
    @Transactional
    public ModelConfig createConfig(ModelConfig config) {
        encryptApiKey(config);
        return modelConfigRepository.save(config);
    }

    /**
     * 更新模型配置（自动加密 apiKey）
     */
    @Transactional
    public ModelConfig updateConfig(Long id, ModelConfig updated) {
        return modelConfigRepository.findById(id).map(config -> {
            if (updated.getName() != null) config.setName(updated.getName());
            if (updated.getProvider() != null) config.setProvider(updated.getProvider());
            if (updated.getType() != null) config.setType(updated.getType());
            if (updated.getApiUrl() != null) config.setApiUrl(updated.getApiUrl());
            if (updated.getApiKey() != null) config.setApiKey(updated.getApiKey());
            if (updated.getModelName() != null) config.setModelName(updated.getModelName());
            if (updated.getMaxTokens() != null) config.setMaxTokens(updated.getMaxTokens());
            if (updated.getIsActive() != null) config.setIsActive(updated.getIsActive());
            if (updated.getPriority() != null) config.setPriority(updated.getPriority());
            if (updated.getWorkflowId() != null) config.setWorkflowId(updated.getWorkflowId());
            if (updated.getBotId() != null) config.setBotId(updated.getBotId());
            if (updated.getAppId() != null) config.setAppId(updated.getAppId());
            if (updated.getIsCozeWorkflow() != null) config.setIsCozeWorkflow(updated.getIsCozeWorkflow());
            // 写入前加密 apiKey（updated.getApiKey() 来自前端明文）
            encryptApiKey(config);
            return modelConfigRepository.save(config);
        }).orElseThrow(() -> new ResourceNotFoundException("模型配置", id));
    }

    /**
     * 删除模型配置
     */
    @Transactional
    public void deleteConfig(Long id) {
        if (!modelConfigRepository.existsById(id)) {
            throw new ResourceNotFoundException("模型配置", id);
        }
        modelConfigRepository.deleteById(id);
    }

    /**
     * 测试模型配置连接
     * <p>
     * 扣子工作流：调用 /v1/workflow/run 验证
     * 通用模型：调用 /models 或 /chat/completions 端点验证
     *
     * @param configId 模型配置ID
     * @return 测试结果 { success, responseTime(ms), message }
     */
    public Map<String, Object> testConnection(Long configId) {
        ModelConfig config = modelConfigRepository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("模型配置", configId));

        // 解密 apiKey 用于测试
        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            apiKey = CryptoUtils.decrypt(apiKey);
        }

        long startTime = System.currentTimeMillis();
        try {
            boolean isCoze = Boolean.TRUE.equals(config.getIsCozeWorkflow());
            if (isCoze) {
                return testCozeWorkflow(config, apiKey, startTime);
            } else {
                return testGenericModel(config, apiKey, startTime);
            }
        } catch (RestClientException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.warn("模型配置连接测试失败 [id={}, name={}]: {}", configId, config.getName(), e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("responseTime", elapsed);
            result.put("message", "连接失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 测试扣子工作流连接
     */
    private Map<String, Object> testCozeWorkflow(ModelConfig config, String apiKey, long startTime) {
        String baseUrl = config.getApiUrl().replaceAll("/+$", "");
        // 扣子工作流验证：调用 /v1/workflow/run 接口（dry-run 风格，仅验证连通性）
        String url = baseUrl + "/v1/workflow/run";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflow_id", config.getWorkflowId());
        body.put("parameters", Collections.emptyMap());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = aiRestTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

        long elapsed = System.currentTimeMillis() - startTime;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("responseTime", elapsed);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("扣子工作流连接测试成功 [id={}, name={}, time={}ms]", config.getId(), config.getName(), elapsed);
            result.put("success", true);
            result.put("message", "扣子工作流连接成功");
        } else {
            result.put("success", false);
            result.put("message", "扣子工作流返回非2xx状态: " + response.getStatusCode());
        }
        return result;
    }

    /**
     * 测试通用模型连接
     * <p>
     * 优先尝试 GET /models，失败则尝试 POST /chat/completions
     */
    private Map<String, Object> testGenericModel(ModelConfig config, String apiKey, long startTime) {
        String baseUrl = config.getApiUrl().replaceAll("/+$", "");

        // 优先尝试 /models 端点（轻量级，不需要消耗 token）
        try {
            String modelsUrl = baseUrl + "/models";
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = aiRestTemplate.exchange(modelsUrl, HttpMethod.GET, entity, Map.class);

            long elapsed = System.currentTimeMillis() - startTime;
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("模型连接测试成功(/models) [id={}, name={}, time={}ms]",
                        config.getId(), config.getName(), elapsed);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("responseTime", elapsed);
                result.put("message", "模型连接成功");
                return result;
            }
        } catch (RestClientException e) {
            log.debug("/models 端点不可用，尝试 /chat/completions: {}", e.getMessage());
        }

        // 回退：尝试 /chat/completions（发送最小请求验证连通性）
        String chatUrl = baseUrl + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> message = Map.of("role", "user", "content", "hi");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModelName());
        body.put("messages", List.of(message));
        body.put("max_tokens", 1);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = aiRestTemplate.exchange(chatUrl, HttpMethod.POST, entity, Map.class);

        long elapsed = System.currentTimeMillis() - startTime;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("responseTime", elapsed);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("模型连接测试成功(/chat/completions) [id={}, name={}, time={}ms]",
                    config.getId(), config.getName(), elapsed);
            result.put("success", true);
            result.put("message", "模型连接成功");
        } else {
            result.put("success", false);
            result.put("message", "模型返回非2xx状态: " + response.getStatusCode());
        }
        return result;
    }

    // ==================== 加解密辅助方法 ====================

    /**
     * 加密实体中的 apiKey（写入前调用）
     */
    private void encryptApiKey(ModelConfig config) {
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            config.setApiKey(CryptoUtils.encrypt(config.getApiKey()));
        }
    }

    /**
     * 解密实体中的 apiKey（读取后调用）
     */
    private ModelConfig decryptApiKey(ModelConfig config) {
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            config.setApiKey(CryptoUtils.decrypt(config.getApiKey()));
        }
        return config;
    }

    /**
     * 批量解密 apiKey（仅限后端内部使用）
     */
    private List<ModelConfig> decryptApiKeys(List<ModelConfig> configs) {
        List<ModelConfig> result = new ArrayList<>(configs.size());
        for (ModelConfig config : configs) {
            result.add(decryptApiKey(config));
        }
        return result;
    }

    /**
     * 对 apiKey 进行脱敏（前4后4位可见，中间用 **** 替代）
     * 用于返回给前端的场景，避免完整 API Key 泄露
     */
    private ModelConfig maskApiKey(ModelConfig config) {
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            // 先解密再脱敏
            String decrypted = CryptoUtils.decrypt(config.getApiKey());
            config.setApiKey(maskString(decrypted));
        }
        return config;
    }

    /**
     * 批量脱敏 apiKey
     */
    private List<ModelConfig> maskApiKeys(List<ModelConfig> configs) {
        List<ModelConfig> result = new ArrayList<>(configs.size());
        for (ModelConfig config : configs) {
            result.add(maskApiKey(config));
        }
        return result;
    }

    /**
     * 字符串脱敏：保留前4后4位，中间用 **** 替代
     * 长度 <= 8 时仅显示前2后2位
     */
    private String maskString(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        if (value.length() <= 8) {
            return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
