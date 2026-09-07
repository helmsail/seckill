package com.helmsail.seckill.base.product;

import java.util.List;

/**
 * 秒杀商品内部服务接口
 */
public interface SeckillProductBizService {

    void addToActivity(AddProductRequest request);

    void removeFromActivity(String activityNo, String spuNo);

    List<SeckillProductDTO> listByActivityNo(String activityNo);
}
