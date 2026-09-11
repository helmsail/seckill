package com.helmsail.seckill.support.server.order;

import com.helmsail.seckill.support.api.order.OrderService;
import com.helmsail.seckill.support.api.order.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 订单 Dubbo 服务实现
 *
 * retries = 0：创建订单无幂等键，自动重试会造成重复订单
 */
@DubboService(retries = 0)
@RequiredArgsConstructor
public class OrderDubboServiceImpl implements OrderService {

    private final OrderBizService orderBizService;

    @Override
    public Long create(CreateOrderRequest request) {
        return orderBizService.create(request);
    }
}
