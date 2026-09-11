package com.helmsail.seckill.support.api.pay;

import lombok.Getter;

/**
 * 支付渠道类型
 */
@Getter
public enum PayChannelType {

    MOCK("mock", "模拟支付");

    private final String code;
    private final String desc;

    PayChannelType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
