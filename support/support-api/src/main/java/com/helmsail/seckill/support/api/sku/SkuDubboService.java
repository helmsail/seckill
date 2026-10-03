package com.helmsail.seckill.support.api.sku;

import com.helmsail.seckill.common.exception.BizException;

import java.util.List;

/**
 * SKU Dubbo 服务接口
 */
public interface SkuDubboService {

    /**
     * 根据 SKU 编号查询
     */
    SkuDTO getBySkuNo(String skuNo) throws BizException;

    /**
     * 根据商品编号查询所有 SKU
     */
    List<SkuDTO> listBySpuNo(String spuNo) throws BizException;

    /**
     * 批量扣减库存（域内单事务：任一项失败整批回滚，零副作用）
     */
    void batchDeductStock(List<StockChangeItem> items) throws BizException;

    /**
     * 批量增加库存（域内单事务：任一项失败整批回滚，零副作用）
     */
    void batchAddStock(List<StockChangeItem> items) throws BizException;
}
