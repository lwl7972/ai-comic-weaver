package com.aicomic.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 配置
 * 访问地址：/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private Integer serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 漫剧制作平台 API")
                        .version("0.1.0")
                        .description("AI 漫剧制作平台后端 API 文档，提供剧本、角色、场景、分镜、导演、S 级模块等完整接口")
                        .termsOfService("https://github.com/lwl7972/ai-comic-weaver")
                        .contact(new Contact()
                                .name("AI Comic Weaver Team")
                                .email("support@aicomic.com")
                                .url("https://github.com/lwl7972/ai-comic-weaver"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("本地开发环境"),
                        new Server()
                                .url("https://api.aicomic.com")
                                .description("生产环境")
                ));
    }
}
