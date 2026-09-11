package com.helmsail.seckill.base.activity;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 活动请求（创建/修改通用）
 */
@Data
public class ActivityRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    private String activityName;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 当天开始时间（禁止跨天，须早于当天结束时间） */
    private LocalTime startTime;

    /** 当天结束时间 */
    private LocalTime endTime;

    /** 周位图：bit0=周一…bit6=周日（127=每天）；空则默认每天 */
    private Integer weekBitmap;

    /** 每人限购数量（0=不限购） */
    private Integer purchaseLimit;

    /** 备注 */
    private String remark;
}
