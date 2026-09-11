package com.helmsail.seckill.base.productsku;

import java.util.List;

/**
 * 活动商品SKU内部服务接口
 */
public interface SeckillProductSkuBizService {

    void batchAdd(AddProductSkuRequest request);

    List<StockRestoreItem> batchRemove(RemoveProductSkuRequest request);

    void batchShelf(ShelfProductSkuRequest request);

    List<SeckillProductSkuDTO> listByActivityNo(String activityNo);

    SeckillProductSkuDTO getByActivityNoAndSkuNo(String activityNo, String skuNo);
}
