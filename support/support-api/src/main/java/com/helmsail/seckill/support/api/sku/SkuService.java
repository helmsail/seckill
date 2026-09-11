package com.helmsail.seckill.support.api.sku;

import com.helmsail.seckill.common.result.PageResult;

import java.util.List;

/**
 * SKU Dubbo 服务接口
 */
public interface SkuService {

    /**
     * 根据 SKU 编号查询
     */
    SkuDTO getBySkuNo(String skuNo);

    /**
     * 根据商品编号查询所有 SKU
     */
    List<SkuDTO> listBySpuNo(String spuNo);

    /**
     * 扣减库存
     */
    void deductStock(String skuNo, int quantity);

    /**
     * 增加库存
     */
    void addStock(String skuNo, int quantity);

    /**
     * 分页查询 SKU
     */
    PageResult<SkuDTO> page(SkuPageQuery query);
}
