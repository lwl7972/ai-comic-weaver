package com.aicomic.common.event;

import org.springframework.context.ApplicationEvent;

/**
 * 资产提取完成事件
 * 用于剧本完成后自动触发角色/场景提取
 */
public class AssetExtractedEvent extends ApplicationEvent {

    private final Long scriptId;
    private final String assetType;
    private final Long projectId;

    public AssetExtractedEvent(Object source, Long projectId, Long scriptId, String assetType) {
        super(source);
        this.projectId = projectId;
        this.scriptId = scriptId;
        this.assetType = assetType;
    }

    public Long getScriptId() {
        return scriptId;
    }

    public String getAssetType() {
        return assetType;
    }

    public Long getProjectId() {
        return projectId;
    }
}
