package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.common.util.CryptoUtils;
import com.aicomic.entity.ModelConfig;
import com.aicomic.repository.ModelConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 模型配置管理服务
 * 管理所有外部 AI 模型 API 的连接参数（ADR-6：模型无关抽象层）
 * <p>
 * apiKey 字段使用 AES-256-CBC 加密存储，读取时自动解密。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;

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
