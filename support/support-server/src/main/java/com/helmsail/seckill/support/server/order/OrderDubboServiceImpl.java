package com.helmsail.seckill.support.server.order;

import com.helmsail.seckill.support.api.order.OrderService;
import com.helmsail.seckill.support.api.order.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 订单 Dubbo 服务实现
 *
 * retries = 0：写路径不做 Dubbo 层自动重试，重投由调用方（MQ 消费/对账）负责
 */
@DubboService(retries = 0)
@RequiredArgsConstructor
public class OrderDubboServiceImpl implements OrderService {

    private final OrderBizService orderBizService;

    @Override
    public Long create(CreateOrderRequest request) {
        return orderBizService.create(request);
    }

    @Override
    public List<String> listExistingOrderNos(List<String> orderNos) {
        return orderBizService.listExistingOrderNos(orderNos);
    }
}
