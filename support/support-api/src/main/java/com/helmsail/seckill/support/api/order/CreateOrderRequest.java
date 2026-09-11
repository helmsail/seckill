package com.helmsail.seckill.support.api.order;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单编号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 订单来源 */
    private OrderSource orderSource;

    /** 原价 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 支付时间 */
    private LocalDateTime paidTime;

    /** 第三方支付流水号 */
    private String tradeNo;
}
