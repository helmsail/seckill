package com.helmsail.seckill.support.api.product;

import lombok.Data;

import jakarta.validation.constraints.Min;
import java.io.Serializable;

/**
 * 商品分页查询请求
 */
@Data
public class ProductPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码 */
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数最小为1")
    private Integer pageSize = 10;
}
