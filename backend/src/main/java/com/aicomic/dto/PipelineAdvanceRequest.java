package com.aicomic.dto;

import com.aicomic.entity.Project;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 流水线阶段推进请求 DTO
 */
@Data
public class PipelineAdvanceRequest {

    /** 目标阶段 */
    @NotNull(message = "目标阶段不能为空")
    private Project.PipelineStage targetStage;

    /** 是否重新执行（跳过脏标记检测，强制重新执行） */
    private boolean reExecute = false;
}
