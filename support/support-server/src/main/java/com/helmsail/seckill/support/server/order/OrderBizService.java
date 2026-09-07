package com.helmsail.seckill.support.server.order;

import com.helmsail.seckill.support.api.order.CreateOrderRequest;

/**
 * 订单内部服务接口
 */
public interface OrderBizService {

    Long create(CreateOrderRequest request);
}
