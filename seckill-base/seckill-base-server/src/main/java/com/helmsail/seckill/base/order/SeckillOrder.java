package com.helmsail.seckill.base.order;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体
 *
 * 分片表：sk_order_0 ~ sk_order_3
 * 审计字段继承自 BaseEntity；主键由 ShardingSphere 雪花算法生成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sk_order")
public class SeckillOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String orderNo;
    private Long userId;
    private String activityNo;
    private String skuNo;
    private Integer quantity;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer orderStatus;
    private LocalDateTime paidTime;
    private String tradeNo;
    private String traceId;
}
