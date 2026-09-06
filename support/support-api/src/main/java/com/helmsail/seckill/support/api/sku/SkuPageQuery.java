package com.helmsail.seckill.support.api.sku;

import lombok.Data;

import jakarta.validation.constraints.Min;
import java.io.Serializable;

/**
 * SKU 分页查询请求
 */
@Data
public class SkuPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品编号（可选，按 SPU 过滤） */
    private String spuNo;

    /** 当前页码 */
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页条数最小为1")
    private Integer pageSize = 10;
}
