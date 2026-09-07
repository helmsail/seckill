package com.helmsail.seckill.base.sku;

import java.util.List;

/**
 * 秒杀 SKU Dubbo 服务接口
 */
public interface SeckillSkuService {

    /**
     * 给商品添加 SKU
     */
    void addSku(AddSkuRequest request);

    /**
     * 给商品删除 SKU
     *
     * @return 删除结果，包含需要归还的库存信息
     */
    RemoveSkuResponse removeSku(RemoveSkuRequest request);

    /**
     * 查询商品下的所有 SKU
     */
    List<SeckillSkuDTO> listBySkProductId(String skProductId);
}
