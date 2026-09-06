package com.helmsail.seckill.support.api.product;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商品分页查询结果
 */
@Data
@AllArgsConstructor
public class ProductPageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据列表 */
    private List<ProductDTO> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;
}
