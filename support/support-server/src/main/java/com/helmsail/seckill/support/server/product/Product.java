package com.helmsail.seckill.support.server.product;

import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Product extends BaseEntity {

    private Long id;
    private String spuNo;
    private String productName;
}
