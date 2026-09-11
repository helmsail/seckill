package com.helmsail.seckill.base.sku;

import com.baomidou.mybatisplus.annotation.TableName;
import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 秒杀 SKU 实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sk_sku")
public class SeckillSku extends BaseEntity {

    private Long id;
    private String skProductId;
    private String skuNo;
    private String skuName;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer activityStock;
    private Integer purchaseLimit;
}
