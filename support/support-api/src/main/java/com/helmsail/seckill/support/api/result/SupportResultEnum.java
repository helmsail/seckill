package com.helmsail.seckill.support.api.result;

import com.helmsail.seckill.common.result.IResultCode;
import lombok.Getter;

/**
 * 主域响应码枚举
 */
@Getter
public enum SupportResultEnum implements IResultCode {

    // ========== 商品 ==========
    PRODUCT_NOT_FOUND("product_not_found", "商品不存在"),

    // ========== SKU ==========
    SKU_NOT_FOUND("sku_not_found", "SKU 不存在"),
    STOCK_INSUFFICIENT("stock_insufficient", "库存不足"),

    // ========== 用户 ==========
    USER_NOT_FOUND("user_not_found", "用户不存在"),
    USER_PASSWORD_ERROR("user_password_error", "密码错误");

    private final String code;
    private final String message;

    SupportResultEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
