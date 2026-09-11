package com.helmsail.seckill.base.order;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建秒杀订单请求
 */
@Data
public class CreateSeckillOrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 原价 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;
}
