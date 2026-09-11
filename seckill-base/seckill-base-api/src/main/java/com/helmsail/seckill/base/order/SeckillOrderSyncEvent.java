package com.helmsail.seckill.base.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单同步事件（秒杀域 → 主域）
 *
 * 仅在支付成功后发送：主域只记载已成交易的成功订单（无状态字段，insert-only）；
 * 未支付/已关闭订单不进主域（用户查询走秒杀域订单表）。
 * 消费端按 orderNo 唯一键幂等（重复消费捕获唯一键冲突视为已同步）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderSyncEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private LocalDateTime paidTime;
    private String tradeNo;
}
