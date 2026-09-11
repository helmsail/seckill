package com.helmsail.seckill.base.activity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 活动实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sk_activity")
public class Activity extends BaseEntity {

    private Long id;
    private String activityNo;
    private String activityName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer weekBitmap;
    private Integer purchaseLimit;
    private Integer activityStatus;
    private String remark;
}
