package com.aicomic.service;

import com.aicomic.entity.GenerationTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 实时推送服务 (ADR-14)
 * 向前端实时推送任务进度、状态变更等事件
 */
@Slf4j
@Service
public class SseService {

    /** 超时时间：30 分钟 */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /** 存储所有活跃的 SSE 连接 */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 连接
     */
    public SseEmitter createEmitter(String clientId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.put(clientId, emitter);

        emitter.onCompletion(() -> emitters.remove(clientId));
        emitter.onTimeout(() -> emitters.remove(clientId));
        emitter.onError(e -> {
            log.warn("SSE 连接异常: clientId={}, error={}", clientId, e.getMessage());
            emitters.remove(clientId);
        });

        // 发送连接成功事件
        try {
            Map<String, String> data = new HashMap<>();
            data.put("clientId", clientId);
            data.put("message", "SSE 连接已建立");
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(data));
        } catch (IOException e) {
            log.error("发送 SSE 连接事件失败: {}", e.getMessage());
        }

        log.debug("SSE 连接建立: clientId={}", clientId);
        return emitter;
    }

    /**
     * 推送任务进度更新
     */
    public void pushTaskProgress(GenerationTask task) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus().name());
        data.put("progress", task.getProgress());
        data.put("targetType", task.getTargetType());
        data.put("targetId", task.getTargetId());
        data.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "");
        broadcast("task-progress", data);
    }

    /**
     * 推送任务完成事件
     */
    public void pushTaskCompleted(GenerationTask task) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        data.put("targetType", task.getTargetType());
        data.put("targetId", task.getTargetId());
        data.put("resultImageUrl", task.getResultImageUrl() != null ? task.getResultImageUrl() : "");
        data.put("resultVideoUrl", task.getResultVideoUrl() != null ? task.getResultVideoUrl() : "");
        broadcast("task-completed", data);
    }

    /**
     * 推送流水线阶段变更
     */
    public void pushPipelineChange(Long projectId, String stage, boolean dirty) {
        Map<String, Object> data = new HashMap<>();
        data.put("projectId", projectId);
        data.put("stage", stage);
        data.put("dirty", dirty);
        broadcast("pipeline-change", data);
    }

    /**
     * 推送通用通知
     */
    public void pushNotification(String type, String message) {
        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("message", message);
        broadcast("notification", data);
    }

    /**
     * 向所有客户端广播事件
     */
    private void broadcast(String eventName, Object data) {
        // 收集发送失败的客户端 ID，遍历结束后统一移除，避免 ConcurrentModificationException
        java.util.List<String> failedClients = new java.util.ArrayList<>();
        emitters.forEach((clientId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                log.warn("SSE 推送失败: clientId={}, event={}", clientId, eventName);
                failedClients.add(clientId);
            }
        });
        failedClients.forEach(emitters::remove);
    }

    /**
     * 获取当前活跃连接数
     */
    public int getActiveConnectionCount() {
        return emitters.size();
    }
}
