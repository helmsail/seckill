package com.helmsail.seckill.base.productsku;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 活动商品SKU DTO
 */
@Data
public class SeckillProductSkuDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String activityNo;
    private String spuNo;
    private String spuName;
    private String skuNo;
    private String skuName;
    private DiscountType discountType;
    private BigDecimal discountParameter;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer activityStock;
    private Integer purchaseLimit;
    /** 上架状态：0=下架，1=上架 */
    private Integer shelfStatus;
}
