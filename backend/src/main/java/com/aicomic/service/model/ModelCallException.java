package com.aicomic.service.model;

/**
 * 模型调用异常
 */
public class ModelCallException extends RuntimeException {

    public ModelCallException(String message) {
        super(message);
    }

    public ModelCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
