package com.helmsail.seckill.base.activity;

import java.time.DayOfWeek;

/**
 * 周位图工具
 *
 * bit0=周一 … bit6=周日
 */
public final class WeekBitmap {

    /** 全选（每天） */
    public static final int ALL = 127;

    private WeekBitmap() {
    }

    /**
     * 判断位图是否命中指定星期（位图为空视为全选）
     */
    public static boolean isActive(Integer weekBitmap, DayOfWeek dayOfWeek) {
        int bitmap = weekBitmap == null ? ALL : weekBitmap;
        return ((bitmap >> (dayOfWeek.getValue() - 1)) & 1) == 1;
    }

    /**
     * 校验位图合法性（1~127）
     */
    public static boolean isValid(Integer weekBitmap) {
        return weekBitmap != null && weekBitmap >= 1 && weekBitmap <= ALL;
    }
}
