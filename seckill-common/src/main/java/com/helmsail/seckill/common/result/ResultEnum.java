package com.helmsail.seckill.common.result;

import lombok.Getter;

/**
 * 响应码枚举
 */
@Getter
public enum ResultEnum implements IResultCode {

    // ========== 通用 ==========
    SUCCESS("success", "操作成功"),
    SYSTEM_ERROR("system_error", "系统异常"),
    PARAM_ERROR("param_error", "参数错误"),
    NOT_FOUND("not_found", "资源不存在"),

    // ========== 活动 ==========
    ACTIVITY_NOT_FOUND("activity_not_found", "活动不存在"),
    ACTIVITY_STATUS_ERROR("activity_status_error", "活动状态不允许此操作"),

    // ========== 订单 ==========
    ORDER_NOT_FOUND("order_not_found", "订单不存在"),
    ORDER_STATUS_ERROR("order_status_error", "订单状态不允许此操作"),

    // ========== 商品 ==========
    PRODUCT_NOT_FOUND("product_not_found", "商品不存在"),
    PRODUCT_NOT_IN_ACTIVITY("product_not_in_activity", "商品未关联此活动"),

    // ========== SKU ==========
    SKU_NOT_FOUND("sku_not_found", "SKU 不存在"),
    SKU_STATUS_ERROR("sku_status_error", "SKU 状态不满足要求"),
    STOCK_INSUFFICIENT("stock_insufficient", "库存不足"),

    // ========== 用户 ==========
    USER_NOT_FOUND("user_not_found", "用户不存在"),
    USER_PASSWORD_ERROR("user_password_error", "密码错误"),
    USER_DISABLED("user_disabled", "用户已被禁用"),

    // ========== 秒杀 ==========
    RATE_LIMITED("rate_limited", "请求过于频繁"),
    BLACKLISTED("blacklisted", "已被限制参与秒杀"),

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
