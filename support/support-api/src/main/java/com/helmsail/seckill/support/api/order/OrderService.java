package com.helmsail.seckill.support.api.order;

/**
 * 订单 Dubbo 服务接口
 */
public interface OrderService {

    /**
     * 创建订单
     */
    Long create(CreateOrderRequest request);
}
