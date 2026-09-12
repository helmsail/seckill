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

    /** 活动编号（关单回补/对账溯源） */
    private String activityNo;

    /** SKU 编号（关单回补/对账溯源） */
    private String skuNo;

    /** 购买数量 */
    private Integer quantity;

    /** 原价 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 幂等键（同一次秒杀请求全局唯一，网关生成）：订单落库去重依据，重复落库时幂等返回已建订单 */
    private String traceId;
}
