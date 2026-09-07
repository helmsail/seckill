package com.helmsail.seckill.base.order;

import lombok.Getter;

/**
 * 秒杀订单状态枚举
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
}
