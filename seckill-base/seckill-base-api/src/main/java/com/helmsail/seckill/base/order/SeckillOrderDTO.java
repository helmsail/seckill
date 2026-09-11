package com.helmsail.seckill.base.order;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单 DTO
 */
@Data
public class SeckillOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private Long userId;
    private String activityNo;
    private String skuNo;
    private Integer quantity;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private SeckillOrderStatus orderStatus;
    private LocalDateTime paidTime;
    private String tradeNo;
}
