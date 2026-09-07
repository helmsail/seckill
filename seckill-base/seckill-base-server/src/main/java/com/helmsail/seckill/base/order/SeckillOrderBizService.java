package com.helmsail.seckill.base.order;

/**
 * 秒杀订单内部服务接口
 */
public interface SeckillOrderBizService {

    String createOrder(CreateSeckillOrderRequest request);

    SeckillOrderDTO getByOrderNo(String orderNo);

    SeckillOrderPageResult pageByUserId(SeckillOrderPageQuery query);

    void paySuccess(String orderNo);

    void closeOrder(String orderNo);
}
