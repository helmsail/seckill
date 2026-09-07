package com.helmsail.seckill.base.activity;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.LocalDateTime;
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

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 生效类型 */
    private EffectiveType effectiveType;

    /** 生效日期（如 1,3,5） */
    private String effectiveDays;

    /** 每日生效开始时间 */
    private LocalTime effectiveStart;

    /** 每日生效结束时间 */
    private LocalTime effectiveEnd;

    /** 每人限购数量（0=不限购） */
    private Integer purchaseLimit;

    /** 备注 */
    private String remark;
}
