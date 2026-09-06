package com.helmsail.seckill.support.server.sku;

import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SKU 实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Sku extends BaseEntity {

    private Long id;
    private String spuNo;
    private String skuNo;
    private String skuName;
    private BigDecimal price;
    private Integer stock;
}
