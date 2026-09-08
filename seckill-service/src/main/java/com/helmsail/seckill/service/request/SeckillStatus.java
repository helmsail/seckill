package com.helmsail.seckill.service.request;

import lombok.Getter;

@Getter
public enum SeckillStatus {

    PENDING("pending", "排队中"),
    SUCCESS("success", "秒杀成功"),
    FAILED("failed", "秒杀失败");

    private final String code;
    private final String desc;

    SeckillStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
