package com.helmsail.seckill.support.api.sku;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU DTO
 */
@Data
public class SkuDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String spuNo;
    private String skuNo;
    private String skuName;
    private BigDecimal price;
    private Integer stock;
}
