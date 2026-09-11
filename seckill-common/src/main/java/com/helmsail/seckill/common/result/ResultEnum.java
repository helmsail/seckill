package com.helmsail.seckill.common.result;

import lombok.Getter;

/**
 * 响应码枚举（仅通用语义，业务错误码见各域 ResultEnum）
 */
@Getter
public enum ResultEnum implements IResultCode {

    // ========== 通用 ==========
    SUCCESS("success", "操作成功"),
    SYSTEM_ERROR("system_error", "系统异常"),
    PARAM_ERROR("param_error", "参数错误"),
    NOT_FOUND("not_found", "资源不存在"),

    // ========== 锁 ==========
    LOCK_ACQUIRE_FAILED("lock_acquire_failed", "获取锁失败"),

    // ========== 远程调用 ==========
    DUBBO_CALL_ERROR("dubbo_call_error", "远程调用异常");

    private final String code;
    private final String message;

    ResultEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
