package com.aicomic.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * AI 模型调用 RestTemplate 配置
 * <p>
 * 使用连接池 + 超时设置，避免每次请求创建新连接。
 * 专用于 AI 模型 HTTP 调用，与常规 Web 请求隔离。
 */
@Configuration
public class AiRestTemplateConfig {

    @Bean(name = "aiRestTemplate")
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);   // 连接超时 10s
        factory.setReadTimeout(120_000);     // 读取超时 120s（AI 生成耗时较长）
        return new RestTemplate(factory);
    }
}
