package com.helmsail.seckill.support.api.sku;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * SKU 分页查询结果
 */
@Data
@AllArgsConstructor
public class SkuPageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<SkuDTO> records;
    private long total;
    private long pageNum;
    private long pageSize;
}
