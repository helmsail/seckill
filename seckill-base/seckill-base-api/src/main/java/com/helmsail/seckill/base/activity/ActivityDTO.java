package com.helmsail.seckill.base.activity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
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
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer weekBitmap;
    private Integer purchaseLimit;
    private ActivityStatus activityStatus;
    private String remark;
}
