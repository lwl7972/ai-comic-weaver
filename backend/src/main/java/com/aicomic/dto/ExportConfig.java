package com.aicomic.dto;

import lombok.Data;

/**
 * 视频导出配置 DTO
 */
@Data
public class ExportConfig {

    /** 导出格式: mp4 / mov / avi */
    private String format = "mp4";

    /** 分辨率: 720p / 1080p / 4K */
    private String resolution = "1080p";

    /** 视频码率 (kbps) */
    private Integer bitrate;

    /** 帧率: 24 / 30 / 60 */
    private Integer fps;
}
