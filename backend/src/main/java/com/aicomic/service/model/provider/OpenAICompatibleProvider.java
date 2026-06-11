package com.aicomic.service.model.provider;

import com.aicomic.entity.ModelConfig;
import com.aicomic.service.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * OpenAI 兼容协议适配器
 * <p>
 * 覆盖：OpenAI、Anthropic（兼容模式）、通义千问、文心一言
 * 这些模型均支持 OpenAI Chat Completions API 格式
 * <p>
 * 使用 Spring 管理的 RestTemplate Bean（已配置连接池），避免每次请求创建新连接
 */
@Slf4j
@Component
public class OpenAICompatibleProvider implements ModelProvider {

    private final ObjectMapper objectMapper;
    private final RestTemplate aiRestTemplate;

    public OpenAICompatibleProvider(ObjectMapper objectMapper,
                                    @Qualifier("aiRestTemplate") RestTemplate aiRestTemplate) {
        this.objectMapper = objectMapper;
        this.aiRestTemplate = aiRestTemplate;
    }

    private static final List<ModelConfig.ModelProvider> SUPPORTED_PROVIDERS = Arrays.asList(
            ModelConfig.ModelProvider.OPENAI,
            ModelConfig.ModelProvider.ANTHROPIC,
            ModelConfig.ModelProvider.QWEN,
            ModelConfig.ModelProvider.ERNIE
    );

    @Override
    public boolean supports(ModelConfig.ModelProvider provider) {
        return SUPPORTED_PROVIDERS.contains(provider);
    }

    @Override
    public TextResponse generateText(TextRequest request) {
        try {
            ModelConfig config = request.getModelConfig();

            // 构建请求体
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getModelName());
            body.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.7);
            if (request.getMaxTokens() != null) {
                body.put("max_tokens", request.getMaxTokens());
            } else if (config.getMaxTokens() != null) {
                body.put("max_tokens", config.getMaxTokens());
            }

            // 构建 messages
            ArrayNode messages = body.putArray("messages");
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                ObjectNode sysMsg = messages.addObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", request.getSystemPrompt());
            }
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", request.getPrompt());

            // 发送请求
            String url = config.getApiUrl() + "/chat/completions";
            HttpHeaders headers = buildHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            log.debug("调用文本模型: provider={}, model={}, url={}", config.getProvider(), config.getModelName(), url);
            ResponseEntity<String> response = aiRestTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.get("choices");
                if (choices != null && choices.size() > 0) {
                    String text = choices.get(0).get("message").get("content").asText();
                    Integer tokensUsed = null;
                    if (root.has("usage") && root.get("usage").has("total_tokens")) {
                        tokensUsed = root.get("usage").get("total_tokens").asInt();
                    }
                    return TextResponse.builder()
                            .success(true)
                            .text(text)
                            .tokensUsed(tokensUsed)
                            .build();
                }
            }

            return TextResponse.builder()
                    .success(false)
                    .errorMessage("模型返回异常: HTTP " + response.getStatusCode())
                    .build();

        } catch (Exception e) {
            log.error("文本模型调用失败: {}", e.getMessage(), e);
            return TextResponse.builder()
                    .success(false)
                    .errorMessage("调用失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public ImageResponse generateImage(ImageRequest request) {
        try {
            ModelConfig config = request.getModelConfig();

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getModelName());
            body.put("prompt", request.getPrompt());
            if (request.getWidth() != null) body.put("width", request.getWidth());
            if (request.getHeight() != null) body.put("height", request.getHeight());
            body.put("n", 1);
            body.put("size", (request.getWidth() != null ? request.getWidth() : 1024) + "x"
                    + (request.getHeight() != null ? request.getHeight() : 1024));

            String url = config.getApiUrl() + "/images/generations";
            HttpHeaders headers = buildHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            log.debug("调用图像模型: provider={}, model={}", config.getProvider(), config.getModelName());
            ResponseEntity<String> response = aiRestTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                if (data != null && data.size() > 0) {
                    String imageUrl = data.get(0).has("url")
                            ? data.get(0).get("url").asText()
                            : data.get(0).get("b64_json").asText();
                    return ImageResponse.builder().success(true).imageUrl(imageUrl).build();
                }
            }

            return ImageResponse.builder().success(false).errorMessage("图像生成返回异常").build();

        } catch (Exception e) {
            log.error("图像模型调用失败: {}", e.getMessage(), e);
            return ImageResponse.builder().success(false).errorMessage("调用失败: " + e.getMessage()).build();
        }
    }

    private HttpHeaders buildHeaders(ModelConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String authHeader = "Bearer " + config.getApiKey();
        headers.set("Authorization", authHeader);

        // 特定供应商的额外 header
        switch (config.getProvider()) {
            case QWEN:
                headers.set("DashScope-SSE", "disable"); // 使用非流式
                break;
            case ERNIE:
                // 文心一言通过 access_token 鉴权，此处假设 api_url 已含 token
                break;
        }
        return headers;
    }
}
