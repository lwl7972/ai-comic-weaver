package com.aicomic.dto;

import lombok.Data;

/**
 * 水印配置 DTO
 */
@Data
public class WatermarkConfig {

    /** 水印类型: TEXT / IMAGE */
    private String type = "TEXT";

    /** 文字水印内容 */
    private String content;

    /** 图片水印路径 */
    private String imagePath;

    /** 水印位置: TOP_LEFT / TOP_RIGHT / BOTTOM_LEFT / BOTTOM_RIGHT */
    private String position = "BOTTOM_RIGHT";

    /** 水印透明度 (0.0 ~ 1.0) */
    private Double opacity = 0.7;

    /** 文字字号 */
    private Integer fontSize = 24;

    /** 文字颜色 */
    private String fontColor = "white";
}
