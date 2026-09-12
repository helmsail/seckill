package com.helmsail.seckill.support.api.sku;

import com.helmsail.seckill.common.exception.BizException;

import java.util.List;

/**
 * SKU Dubbo 服务接口
 */
public interface SkuService {

    /**
     * 根据 SKU 编号查询
     */
    SkuDTO getBySkuNo(String skuNo) throws BizException;

    /**
     * 根据商品编号查询所有 SKU
     */
    List<SkuDTO> listBySpuNo(String spuNo) throws BizException;

    /**
     * 根据名称模糊查询 SKU
     */
    List<SkuDTO> listBySkuName(String skuName) throws BizException;

    /**
     * 扣减库存
     */
    void deductStock(String skuNo, int quantity) throws BizException;

    /**
     * 增加库存
     */
    void addStock(String skuNo, int quantity) throws BizException;
}
