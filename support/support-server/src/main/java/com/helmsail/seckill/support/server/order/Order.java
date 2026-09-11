package com.helmsail.seckill.support.server.order;

import com.baomidou.mybatisplus.annotation.TableName;
import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class Order extends BaseEntity {

    private Long id;
    private String orderNo;
    private Long userId;
    private String orderSource;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private LocalDateTime paidTime;
    private String remark;
}
