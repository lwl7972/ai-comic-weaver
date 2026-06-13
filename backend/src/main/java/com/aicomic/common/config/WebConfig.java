package com.aicomic.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置
 * 桌面端通过随机端口隔离，允许跨域请求
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.base-dir:./data}")
    private String storageBaseDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path outputDir = Paths.get(storageBaseDir).toAbsolutePath();
        registry.addResourceHandler("/output/**")
                .addResourceLocations("file:" + outputDir + "/")
                .setCachePeriod(0);
    }
}
