package com.helmsail.seckill.base.activity;

import lombok.Getter;

/**
 * 活动状态枚举
 *
 * 状态流转：
 * 0(待开始) → 1(进行中) → 3(已结束)
 *              ↕
 *          2(已暂停)
 */
@Getter
public enum ActivityStatus {

    PENDING(0, "待开始"),
    ACTIVE(1, "进行中"),
    PAUSED(2, "已暂停"),
    ENDED(3, "已结束");

    private final int code;
    private final String desc;

    ActivityStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 校验状态流转是否合法
     */
    public static boolean canTransit(ActivityStatus from, ActivityStatus to) {
        if (from == PENDING && to == ACTIVE) return true;
        if (from == ACTIVE && to == PAUSED) return true;
        if (from == ACTIVE && to == ENDED) return true;
        if (from == PAUSED && to == ACTIVE) return true;
        if (from == PAUSED && to == ENDED) return true;
        return false;
    }
}
