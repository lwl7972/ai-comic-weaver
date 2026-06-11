package com.aicomic.service.model;

import com.aicomic.entity.ModelConfig;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 模型供应商抽象接口（ADR-6 模型无关化）
 * <p>
 * 所有外部 AI 模型（文本/图像/视频/扣子工作流）统一通过此接口调用。
 * 各供应商实现此接口，由 ModelCallService 根据配置自动路由。
 */
public interface ModelProvider {

    /**
     * 判断是否支持指定的供应商类型
     */
    boolean supports(ModelConfig.ModelProvider provider);

    // ==================== 文本生成 ====================

    /**
     * 同步调用文本模型
     */
    default TextResponse generateText(TextRequest request) {
        throw new UnsupportedOperationException("该模型供应商不支持文本生成");
    }

    // ==================== 图像生成 ====================

    /**
     * 同步调用图像模型
     */
    default ImageResponse generateImage(ImageRequest request) {
        throw new UnsupportedOperationException("该模型供应商不支持图像生成");
    }

    // ==================== 视频生成 ====================

    /**
     * 异步提交视频生成任务
     */
    default VideoResponse submitVideo(VideoRequest request) {
        throw new UnsupportedOperationException("该模型供应商不支持视频生成");
    }

    /**
     * 查询视频生成任务结果
     */
    default VideoResponse pollVideoResult(String taskId, ModelConfig config) {
        throw new UnsupportedOperationException("该模型供应商不支持视频生成");
    }

    // ==================== 扣子工作流 ====================

    /**
     * 异步提交扣子工作流
     */
    default CozeWorkflowResponse submitWorkflow(CozeWorkflowRequest request) {
        throw new UnsupportedOperationException("该模型供应商不支持扣子工作流");
    }

    /**
     * 查询扣子工作流执行结果
     */
    default CozeWorkflowResponse pollWorkflowResult(String runId, ModelConfig config) {
        throw new UnsupportedOperationException("该模型供应商不支持扣子工作流");
    }
}
