package com.aicomic.common.event;

import org.springframework.context.ApplicationEvent;

/**
 * 流水线阶段完成事件
 * 用于触发下游阶段的脏标记检查
 */
public class PipelineStageCompletedEvent extends ApplicationEvent {

    private final Long projectId;
    private final String completedStage;

    public PipelineStageCompletedEvent(Object source, Long projectId, String completedStage) {
        super(source);
        this.projectId = projectId;
        this.completedStage = completedStage;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getCompletedStage() {
        return completedStage;
    }
}
