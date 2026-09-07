package com.helmsail.seckill.base.product;

import java.util.List;

/**
 * 秒杀商品 Dubbo 服务接口
 */
public interface SeckillProductService {

    /**
     * 活动添加商品
     */
    void addToActivity(AddProductRequest request);

    /**
     * 活动移除商品
     */
    void removeFromActivity(String activityNo, String spuNo);

    /**
     * 查询活动下的所有商品
     */
    List<SeckillProductDTO> listByActivityNo(String activityNo);
}
