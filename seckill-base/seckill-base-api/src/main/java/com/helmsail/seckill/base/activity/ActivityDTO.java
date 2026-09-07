package com.helmsail.seckill.base.activity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 活动 DTO
 */
@Data
public class ActivityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String activityNo;
    private String activityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private EffectiveType effectiveType;
    private String effectiveDays;
    private LocalTime effectiveStart;
    private LocalTime effectiveEnd;
    private Integer purchaseLimit;
    private ActivityStatus activityStatus;
    private String remark;
}
