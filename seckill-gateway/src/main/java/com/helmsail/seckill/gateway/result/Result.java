package com.helmsail.seckill.gateway.result;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 网关统一响应
 *
 * JSON 结构与下游服务的 Result 保持一致：
 * { "code": "...", "message": "...", "data": ... }
 */
@Data
@AllArgsConstructor
public class Result<T> {

    private String code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>("success", "操作成功", data);
    }

    public static Result<Void> fail(GatewayError error) {
        return new Result<>(error.getCode(), error.getMessage(), null);
    }

    public static Result<Void> fail(GatewayError error, String message) {
        return new Result<>(error.getCode(), message, null);
    }
}
