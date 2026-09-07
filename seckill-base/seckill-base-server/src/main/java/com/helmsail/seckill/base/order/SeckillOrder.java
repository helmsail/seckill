package com.helmsail.seckill.base.order;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体
 *
 * 分片表：sk_order_0 ~ sk_order_3
 */
@Data
@TableName("sk_order")
public class SeckillOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer orderStatus;
    private LocalDateTime paidTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
