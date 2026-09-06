package com.helmsail.seckill.support.api.product;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品分页查询请求
 */
@Data
public class ProductPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
