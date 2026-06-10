package com.aicomic.common.exception;

/**
 * 资源不存在异常
 * 用于 Service 层抛出，由 GlobalExceptionHandler 统一处理
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceName, Object resourceId) {
        super(resourceName + "不存在: " + resourceId);
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceName = null;
        this.resourceId = null;
    }

    public String getResourceName() { return resourceName; }
    public Object getResourceId() { return resourceId; }
}
