package com.helmsail.seckill.base.activity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 活动生效窗口判定（状态=进行中 + 日期范围 + 当天时段 + 周位图）
 *
 * 多端复用：service 前置过滤（缓存数据）、processor 权威终判（DB 数据）。
 */
public final class ActivityWindows {

    private ActivityWindows() {}

    public static boolean isInEffectiveWindow(ActivityDTO activity, LocalDateTime now) {
        if (activity == null || activity.getActivityStatus() != ActivityStatus.ACTIVE) {
            return false;
        }
        LocalDate today = now.toLocalDate();
        if (today.isBefore(activity.getStartDate()) || today.isAfter(activity.getEndDate())) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        if (time.isBefore(activity.getStartTime()) || time.isAfter(activity.getEndTime())) {
            return false;
        }
        return WeekBitmap.isActive(activity.getWeekBitmap(), today.getDayOfWeek());
    }
}
