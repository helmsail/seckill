package com.helmsail.seckill.support.api.pay;

/**
 * 交易状态枚举（统一内部状态）
 *
 * 各支付渠道的原生状态统一归一化为以下状态。
 */
public enum PayTradeStatus {

    /** 待支付 */
    PENDING,

    /** 已支付 */
    PAID,

    /** 已关闭 */
    CLOSED;
}
