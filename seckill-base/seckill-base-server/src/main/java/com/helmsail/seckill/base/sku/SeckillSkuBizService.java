package com.helmsail.seckill.base.sku;

import java.util.List;

/**
 * 秒杀 SKU 内部服务接口
 */
public interface SeckillSkuBizService {

    void addSku(AddSkuRequest request);

    RemoveSkuResponse removeSku(RemoveSkuRequest request);

    List<SeckillSkuDTO> listBySkProductId(String skProductId);
}
