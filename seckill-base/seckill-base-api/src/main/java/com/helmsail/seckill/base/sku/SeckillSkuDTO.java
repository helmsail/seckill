package com.helmsail.seckill.base.sku;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀 SKU DTO
 */
@Data
public class SeckillSkuDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String skProductId;
    private String skuNo;
    private String skuName;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer activityStock;
    private Integer purchaseLimit;
}
