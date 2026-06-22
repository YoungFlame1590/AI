package com.printshop.common.api;

/**
 * 标准 HTTP JSON 响应包装。
 *
 * @param code HTTP 状态码
 * @param message 响应说明
 * @param data 业务数据
 * @param traceId 审计追踪标识
 * @param <T> 业务数据类型
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(200, "OK", data, traceId);
    }

    public static <T> ApiResponse<T> error(int code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
