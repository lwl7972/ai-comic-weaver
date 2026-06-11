package com.aicomic.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 剧集创建/更新请求 DTO
 */
@Data
public class EpisodeRequest {

    private Integer episodeNumber;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    private String scriptContent;

    private String parsedData;

    private String status;
}
