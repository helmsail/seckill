package com.helmsail.seckill.base.activity;

import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 活动实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Activity extends BaseEntity {

    private Long id;
    private String activityNo;
    private String activityName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer effectiveType;
    private String effectiveDays;
    private LocalTime effectiveStart;
    private LocalTime effectiveEnd;
    private Integer purchaseLimit;
    private Integer activityStatus;
    private String remark;
}
