package com.aicomic.service.model.provider;

import com.aicomic.entity.ModelConfig;
import com.aicomic.service.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 扣子工作流适配器（ADR-15: 异步为主）
 * <p>
 * 覆盖：Coze 工作流 API
 * 支持：异步提交 + 轮询查询结果
 */
@Slf4j
@Component
public class CozeWorkflowProvider implements ModelProvider {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public CozeWorkflowProvider(ObjectMapper objectMapper,
                                @Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    private static final String COZE_API_BASE = "https://api.coze.cn";
    private static final long POLL_INTERVAL_MS = 3000;
    private static final long POLL_TIMEOUT_MS = 10 * 60 * 1000; // 10分钟

    @Override
    public boolean supports(ModelConfig.ModelProvider provider) {
        return provider == ModelConfig.ModelProvider.COZE;
    }

    @Override
    public CozeWorkflowResponse submitWorkflow(CozeWorkflowRequest request) {
        try {
            ModelConfig config = request.getModelConfig();

            // 构建请求体
            ObjectNode body = objectMapper.createObjectNode();
            String workflowId = request.getWorkflowId() != null ? request.getWorkflowId() : config.getWorkflowId();
            body.put("workflow_id", workflowId);

            if (request.getParameters() != null) {
                // parameters 可以是 JSON 字符串或 JSON 对象
                try {
                    body.set("parameters", objectMapper.readTree(request.getParameters()));
                } catch (Exception e) {
                    body.put("parameters", request.getParameters());
                }
            }

            if (request.getBotId() != null) {
                body.put("bot_id", request.getBotId());
            } else if (config.getBotId() != null) {
                body.put("bot_id", config.getBotId());
            }

            if (request.getAppId() != null) {
                body.put("app_id", request.getAppId());
            } else if (config.getAppId() != null) {
                body.put("app_id", config.getAppId());
            }

            // 异步模式
            body.put("async_mode", true);

            // 发送请求
            String url = COZE_API_BASE + "/v1/workflow/run";
            HttpHeaders headers = buildCozeHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            log.info("提交扣子工作流: workflowId={}", workflowId);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String code = root.has("code") ? root.get("code").asText() : "";

                if ("0".equals(code)) {
                    String debugUrl = root.has("debug_url") ? root.get("debug_url").asText() : null;
                    // 异步模式下返回数据包含 execute_id 或直接在 data 中
                    String data = root.has("data") ? root.get("data").asText() : null;
                    return CozeWorkflowResponse.builder()
                            .success(true)
                            .runId(data) // 异步模式下 data 为 execute_id
                            .status("SUBMITTED")
                            .debugUrl(debugUrl)
                            .build();
                } else {
                    String msg = root.has("msg") ? root.get("msg").asText() : "未知错误";
                    return CozeWorkflowResponse.builder().success(false).errorMessage(msg).build();
                }
            }

            return CozeWorkflowResponse.builder()
                    .success(false)
                    .errorMessage("HTTP " + response.getStatusCode())
                    .build();

        } catch (Exception e) {
            log.error("扣子工作流提交失败: {}", e.getMessage(), e);
            return CozeWorkflowResponse.builder()
                    .success(false)
                    .errorMessage("提交失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public CozeWorkflowResponse pollWorkflowResult(String runId, ModelConfig config) {
        try {
            String url = COZE_API_BASE + "/v1/workflows/runs/" + runId;
            HttpHeaders headers = buildCozeHeaders(config);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String code = root.has("code") ? root.get("code").asText() : "";

                if ("0".equals(code)) {
                    JsonNode dataNode = root.get("data");
                    if (dataNode != null) {
                        String status = dataNode.has("status") ? dataNode.get("status").asText() : "";
                        String output = dataNode.has("output") ? dataNode.get("output").asText() : null;

                        switch (status) {
                            case "SUCCESS":
                                return CozeWorkflowResponse.builder()
                                        .success(true).output(output)
                                        .status("COMPLETED").runId(runId).build();
                            case "RUNNING":
                                return CozeWorkflowResponse.builder()
                                        .success(true).status("PROCESSING").runId(runId).build();
                            case "FAILED":
                                String errMsg = dataNode.has("error_message") ? dataNode.get("error_message").asText() : "执行失败";
                                return CozeWorkflowResponse.builder()
                                        .success(false).status("FAILED").errorMessage(errMsg).runId(runId).build();
                            default:
                                return CozeWorkflowResponse.builder()
                                        .success(true).status("PROCESSING").runId(runId).build();
                        }
                    }
                }
            }

            return CozeWorkflowResponse.builder()
                    .success(false)
                    .status("FAILED")
                    .errorMessage("查询失败: HTTP " + response.getStatusCode())
                    .build();

        } catch (Exception e) {
            log.error("扣子工作流轮询失败: runId={}, error={}", runId, e.getMessage());
            return CozeWorkflowResponse.builder()
                    .success(false)
                    .status("FAILED")
                    .errorMessage("轮询失败: " + e.getMessage())
                    .build();
        }
    }

    private HttpHeaders buildCozeHeaders(ModelConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());
        return headers;
    }
}
