package com.helmsail.seckill.base.product;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀商品 DTO
 */
@Data
public class SeckillProductDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String activityNo;
    private String spuNo;
    private String spuName;
    private DiscountType discountType;
    private BigDecimal discountParameter;
    private Integer sortOrder;
}
