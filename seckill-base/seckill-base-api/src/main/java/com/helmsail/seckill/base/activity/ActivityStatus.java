package com.helmsail.seckill.base.activity;

import lombok.Getter;

/**
 * 活动状态枚举
 *
 * 状态流转：
 * 0(待开始) →activate→ 1(进行中) ⇄pause/resume⇄ 2(已暂停)
 *                         │close                │close
 *                         ▼                     ▼
 *                      3(已关闭) ←──────────────┘
 */
@Getter
public enum ActivityStatus {

    PENDING(0, "待开始"),
    ACTIVE(1, "进行中"),
    PAUSED(2, "已暂停"),
    CLOSED(3, "已关闭");

    private final int code;
    private final String desc;

    ActivityStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 查找状态
     */
    public static ActivityStatus byCode(int code) {
        for (ActivityStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知活动状态: " + code);
    }

    /**
     * 校验状态流转是否合法
     */
    public static boolean canTransit(ActivityStatus from, ActivityStatus to) {
        if (from == PENDING && to == ACTIVE) return true;
        if (from == ACTIVE && to == PAUSED) return true;
        if (from == ACTIVE && to == CLOSED) return true;
        if (from == PAUSED && to == ACTIVE) return true;
        if (from == PAUSED && to == CLOSED) return true;
        return false;
    }
}
