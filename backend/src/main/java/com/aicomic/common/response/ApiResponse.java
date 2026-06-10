package com.aicomic.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一API响应格式 (6.4.2)
 */
@Getter
@AllArgsConstructor
public class ApiResponse<T> {
    private final int code;
    private final String message;
    private final T data;
    private final long timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, System.currentTimeMillis());
    }

    public static final int SUCCESS = 0;
    public static final int PARAM_ERROR = 10001;
    public static final int NOT_FOUND = 10002;
    public static final int FORBIDDEN = 10003;
    public static final int CONFIG_NOT_FOUND = 10004;
    public static final int TASK_EXISTS = 20001;
    public static final int TASK_FAILED = 20002;
    public static final int TASK_TIMEOUT = 20003;
    public static final int PIPELINE_CONFLICT = 30001;
}
