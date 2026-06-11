package com.aicomic.dto;

import lombok.Data;

import java.util.List;

/**
 * 分镜创建/更新请求 DTO
 */
@Data
public class StoryboardRequest {
    private Long id;
    private Long episodeId;
    private Integer sequence;
    private String timeRange;
    private String continuity;
    private String dialogue;
    private String action;
    private String emotion;
    private String shotSize;
    private String cameraAngle;
    private String cameraMovement;
    private String involvedCharacters;
    private String involvedSceneName;
    private String bgSound;
    private String generationPurpose;
}

/**
 * 批量编辑分镜请求
 */
@Data
class BatchUpdateStoryboardRequest {
    private List<StoryboardRequest> storyboards;
}
