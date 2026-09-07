package com.helmsail.seckill.base.activity;

import lombok.Getter;

/**
 * 生效类型枚举
 */
@Getter
public enum EffectiveType {

    UNLIMITED(0, "不限"),
    PERIODIC(1, "周期性");

    private final int code;
    private final String desc;

    EffectiveType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
