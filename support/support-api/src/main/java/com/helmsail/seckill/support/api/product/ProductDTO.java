package com.helmsail.seckill.support.api.product;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品 DTO
 */
@Data
public class ProductDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String spuNo;
    private String productName;
}
