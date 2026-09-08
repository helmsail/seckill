package com.helmsail.seckill.base.order;

import lombok.Getter;

/**
 * 秒杀订单状态枚举
 *
 * 状态流转：
 * PENDING → PAID
 * PENDING → CLOSED
 */
@Getter
public enum SeckillOrderStatus {

    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    CLOSED(2, "已关闭");

    private final int code;
    private final String desc;

    SeckillOrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 校验状态流转是否合法
     */
    public static boolean canTransit(SeckillOrderStatus from, SeckillOrderStatus to) {
        if (from == PENDING && to == PAID) return true;
        if (from == PENDING && to == CLOSED) return true;
        return false;
    }
}
