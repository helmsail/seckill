package com.helmsail.seckill.common.result;

import lombok.Getter;

import java.io.Serializable;

/**
 * 统一响应封装
 */
@Getter
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String message;
    private final T data;
    private final Boolean success;

    private Result(String code, String message, T data, Boolean success) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = success;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultEnum.SUCCESS.getCode(), ResultEnum.SUCCESS.getMessage(), data, true);
    }

    public static <T> Result<T> of(String code, String message) {
        return of(code, message, null);
    }

    public static <T> Result<T> of(String code, String message, T data) {
        return new Result<>(code, message, data, false);
    }

    public static <T> Result<T> of(IResultCode resultCode) {
        return of(resultCode, null);
    }

    public static <T> Result<T> of(IResultCode resultCode, T data) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), data,
                ResultEnum.SUCCESS.getCode().equals(resultCode.getCode()));
    }
}
