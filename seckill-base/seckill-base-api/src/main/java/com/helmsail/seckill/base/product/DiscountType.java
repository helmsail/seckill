package com.helmsail.seckill.base.product;

import lombok.Getter;

/**
 * 折扣类型枚举
 */
@Getter
public enum DiscountType {

    FIXED_PRICE(0, "固定秒杀价"),
    DISCOUNT(1, "折扣"),
    FIXED_REDUCTION(2, "固定扣减");

    private final int code;
    private final String desc;

    DiscountType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
