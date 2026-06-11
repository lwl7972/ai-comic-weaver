package com.aicomic.service.model;

import com.aicomic.entity.ModelConfig;
import com.aicomic.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 模型调用服务 - 统一入口（ADR-6 模型无关抽象层）
 * <p>
 * 职责：
 * 1. 根据模型类型/用途选择合适的 ModelProvider 实现
 * 2. API Key 轮询（按优先级选取激活的模型配置）
 * 3. 调用外部 API 并返回结果
 * 4. 异常处理 + 重试
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCallService {

    private final ModelConfigService modelConfigService;
    private final List<ModelProvider> providers;

    /**
     * 调用文本模型（同步）
     *
     * @param type    模型类型
     * @param prompt  提示词
     * @return 模型返回的文本结果
     */
    public String callText(ModelConfig.ModelType type, String prompt) {
        return callText(type, prompt, null);
    }

    /**
     * 调用文本模型（同步，指定项目偏好模型ID）
     *
     * @param type          模型类型
     * @param prompt        提示词
     * @param preferredModelId 优先使用的模型配置ID（可为null）
     * @return 模型返回的文本结果
     */
    public String callText(ModelConfig.ModelType type, String prompt, Long preferredModelId) {
        ModelConfig config = resolveConfig(type, preferredModelId);
        ModelProvider provider = resolveProvider(config);
        TextRequest request = TextRequest.builder()
                .prompt(prompt)
                .modelConfig(config)
                .build();
        TextResponse response = provider.generateText(request);
        if (!response.isSuccess()) {
            throw new ModelCallException("文本模型调用失败: " + response.getErrorMessage());
        }
        return response.getText();
    }

    /**
     * 调用图像模型（同步）
     */
    public String callImage(String prompt, Long preferredModelId) {
        ModelConfig config = resolveConfig(ModelConfig.ModelType.IMAGE, preferredModelId);
        ModelProvider provider = resolveProvider(config);
        ImageRequest request = ImageRequest.builder()
                .prompt(prompt)
                .modelConfig(config)
                .build();
        ImageResponse response = provider.generateImage(request);
        if (!response.isSuccess()) {
            throw new ModelCallException("图像模型调用失败: " + response.getErrorMessage());
        }
        return response.getImageUrl();
    }

    /**
     * 调用视频模型（异步提交）
     *
     * @return 任务ID或run_id
     */
    public String submitVideo(String prompt, String imageUrl, Long preferredModelId) {
        ModelConfig config = resolveConfig(ModelConfig.ModelType.VIDEO, preferredModelId);
        ModelProvider provider = resolveProvider(config);
        VideoRequest request = VideoRequest.builder()
                .prompt(prompt)
                .imageUrl(imageUrl)
                .modelConfig(config)
                .build();
        VideoResponse response = provider.submitVideo(request);
        if (!response.isSuccess()) {
            throw new ModelCallException("视频任务提交失败: " + response.getErrorMessage());
        }
        return response.getTaskId();
    }

    /**
     * 查询异步任务结果
     */
    public VideoResponse pollVideoResult(String taskId, Long modelConfigId) {
        ModelConfig config = modelConfigService.getDecryptedConfig(modelConfigId)
                .orElseThrow(() -> new ModelCallException("模型配置不存在: " + modelConfigId));
        ModelProvider provider = resolveProvider(config);
        return provider.pollVideoResult(taskId, config);
    }

    /**
     * 调用扣子工作流（异步提交）
     */
    public String submitCozeWorkflow(String workflowId, String parameters, Long modelConfigId) {
        ModelConfig config = modelConfigService.getDecryptedConfig(modelConfigId)
                .orElseThrow(() -> new ModelCallException("模型配置不存在: " + modelConfigId));
        ModelProvider provider = resolveProvider(config);
        CozeWorkflowRequest request = CozeWorkflowRequest.builder()
                .workflowId(workflowId)
                .parameters(parameters)
                .modelConfig(config)
                .build();
        CozeWorkflowResponse response = provider.submitWorkflow(request);
        if (!response.isSuccess()) {
            throw new ModelCallException("扣子工作流提交失败: " + response.getErrorMessage());
        }
        return response.getRunId();
    }

    /**
     * 查询扣子工作流结果
     */
    public CozeWorkflowResponse pollCozeWorkflow(String runId, Long modelConfigId) {
        ModelConfig config = modelConfigService.getDecryptedConfig(modelConfigId)
                .orElseThrow(() -> new ModelCallException("模型配置不存在: " + modelConfigId));
        ModelProvider provider = resolveProvider(config);
        return provider.pollWorkflowResult(runId, config);
    }

    // ==================== 内部方法 ====================

    /**
     * 根据类型选取激活的模型配置（按优先级轮询）
     */
    private ModelConfig resolveConfig(ModelConfig.ModelType type, Long preferredModelId) {
        // 优先使用指定模型
        if (preferredModelId != null) {
            Optional<ModelConfig> preferred = modelConfigService.getDecryptedConfig(preferredModelId);
            if (preferred.isPresent() && preferred.get().getIsActive()) {
                return preferred.get();
            }
            log.warn("指定的模型配置 {} 不可用，回退到按优先级选取", preferredModelId);
        }

        List<ModelConfig> activeConfigs = modelConfigService.getDecryptedActiveConfigs();
        List<ModelConfig> matched = activeConfigs.stream()
                .filter(c -> c.getType() == type && c.getIsActive())
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            // 如果是工作流类型，也尝试 WORKFLOW 类型
            if (type != ModelConfig.ModelType.WORKFLOW) {
                List<ModelConfig> workflowConfigs = activeConfigs.stream()
                        .filter(c -> c.getType() == ModelConfig.ModelType.WORKFLOW && c.getIsActive())
                        .collect(Collectors.toList());
                if (!workflowConfigs.isEmpty()) {
                    log.warn("未找到 {} 类型的模型配置，回退到 WORKFLOW 类型配置 [id={}]，可能无法满足请求",
                            type, workflowConfigs.get(0).getId());
                    return workflowConfigs.get(0);
                }
            }
            throw new ModelCallException("没有可用的" + type + "模型配置，请在配置中心添加并激活模型");
        }

        return matched.get(0); // 按优先级排序后取第一个
    }

    /**
     * 根据模型配置的 provider 类型选取对应的 ModelProvider 实现
     */
    private ModelProvider resolveProvider(ModelConfig config) {
        return providers.stream()
                .filter(p -> p.supports(config.getProvider()))
                .findFirst()
                .orElseThrow(() -> new ModelCallException(
                        "不支持的模型供应商: " + config.getProvider() + "，请检查模型配置"));
    }
}
