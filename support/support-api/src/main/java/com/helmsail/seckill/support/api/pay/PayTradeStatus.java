package com.helmsail.seckill.support.api.pay;

/**
 * 交易状态（统一内部状态）
 *
 * 各支付渠道的原生状态统一归一化为以下状态。
 */
public final class PayTradeStatus {

    private PayTradeStatus() {}

    /** 待支付 */
    public static final String PENDING = "PENDING";

    /** 已支付 */
    public static final String PAID = "PAID";

    /** 已关闭 */
    public static final String CLOSED = "CLOSED";
}
