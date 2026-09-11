package com.helmsail.seckill.support.server.order;

import com.helmsail.seckill.support.api.order.CreateOrderRequest;

import java.util.List;

/**
 * 订单内部服务接口
 */
public interface OrderBizService {

    Long create(CreateOrderRequest request);

    List<String> listExistingOrderNos(List<String> orderNos);
}
