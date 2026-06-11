package com.aicomic.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 角色创建/更新请求 DTO
 */
@Data
public class CharacterRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String name;

    private String role;

    private String gender;

    @Size(max = 20, message = "年龄段长度不能超过20")
    private String ageRange;

    private String appearance;

    private String personality;

    private String anchorPrompt;

    private Long referenceImageId;
}
