package com.helmsail.seckill.support.api.order;

import lombok.Getter;

/**
 * 订单来源枚举
 */
@Getter
public enum OrderSource {

    MAIN("main", "主域"),
    SECKILL("seckill", "秒杀域");

    private final String code;
    private final String desc;

    OrderSource(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
