package com.aicomic.common.exception;

import com.aicomic.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 - 统一错误码体系 (6.4.2)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static class BusinessException extends RuntimeException {
        private final int code;

        public BusinessException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int code() { return code; }
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException e) {
        return ApiResponse.error(e.code(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        // TODO: add logging
        return ApiResponse.error(500, "服务器内部错误: " + e.getMessage());
    }
}
