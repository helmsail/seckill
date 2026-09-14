package com.helmsail.seckill.service.order;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态查询结果
 *
 * 本质是秒杀订单状态（见 SeckillOrderStatus）：PENDING（待支付）/
 * PAID（已支付）/ CLOSED（已关闭）。
 * 数据以订单表为权威事实源，状态由支付回调写路径推进。
 */
@Data
public class OrderStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private String orderNo;

    /** 订单状态（SeckillOrderStatus 名称：PENDING/PAID/CLOSED） */
    private String status;

    /** 支付时间（status=PAID 时返回） */
    private LocalDateTime paidTime;
}
