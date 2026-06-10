package com.aicomic.controller;

import com.aicomic.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SSE 实时推送端点 (ADR-14)
 */
@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    /**
     * GET /api/v1/sse/connect
     * 建立 SSE 长连接，接收任务进度推送
     */
    @GetMapping("/connect")
    public SseEmitter connect() {
        String clientId = UUID.randomUUID().toString().substring(0, 8);
        return sseService.createEmitter(clientId);
    }

    /**
     * GET /api/v1/sse/status
     * 查询 SSE 连接状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("activeConnections", sseService.getActiveConnectionCount());
        return result;
    }
}
