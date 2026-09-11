package com.helmsail.seckill.base.result;

import com.helmsail.seckill.common.result.IResultCode;
import lombok.Getter;

/**
 * 秒杀域响应码枚举
 */
@Getter
public enum SeckillResultEnum implements IResultCode {

    // ========== 活动 ==========
    ACTIVITY_NOT_FOUND("activity_not_found", "活动不存在"),
    ACTIVITY_STATUS_ERROR("activity_status_error", "活动状态不允许此操作"),
    ACTIVITY_PARAM_ERROR("activity_param_error", "活动参数不合法"),
    ACTIVITY_NOT_EFFECTIVE("activity_not_effective", "活动不在生效时段"),
    ACTIVITY_HAS_SKU("activity_has_sku", "活动下存在商品，不可删除"),

    // ========== 秒杀商品 / SKU ==========
    SKU_NOT_FOUND("sku_not_found", "秒杀 SKU 不存在"),
    SKU_ALREADY_EXISTS("sku_already_exists", "SKU 已在活动中"),
    SKU_OFF_SHELF("sku_off_shelf", "秒杀商品已下架"),
    STOCK_INSUFFICIENT("stock_insufficient", "库存不足"),

    // ========== 秒杀 ==========
    RATE_LIMITED("rate_limited", "请求过于频繁"),
    BLACKLISTED("blacklisted", "已被限制参与秒杀"),

    // ========== 订单 ==========
    ORDER_NOT_FOUND("order_not_found", "订单不存在"),
    ORDER_STATUS_NOT_ALLOWED("order_status_not_allowed", "订单状态不允许此操作"),
    PAY_AMOUNT_MISMATCH("pay_amount_mismatch", "支付金额与订单不一致");

    private final String code;
    private final String message;

    SeckillResultEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
