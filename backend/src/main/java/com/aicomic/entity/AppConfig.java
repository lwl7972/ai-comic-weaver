package com.aicomic.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 5.14 应用全局配置表 - KV键值对存储
 *
 * 预置配置项:
 *   - brand_logo_path / app_name / splashscreen_path
 *   - auto_update_enabled / update_channel / check_interval (8.1.5)
 *   - default_style_type / default_shot_size
 *   - ffmpeg_path / temp_cleanup_days
 *   - auto_save_interval
 */
@Entity
@Table(name = "app_config")
@Data
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String key;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String value;

    private String description;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
