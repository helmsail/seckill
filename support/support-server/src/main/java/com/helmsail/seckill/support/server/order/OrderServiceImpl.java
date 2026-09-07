package com.helmsail.seckill.support.server.order;

import com.helmsail.seckill.support.api.order.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 订单服务实现
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderBizService {

    private final OrderMapper orderMapper;

    @Override
    public Long create(CreateOrderRequest request) {
        Order order = new Order();
        order.setOrderNo(request.getOrderNo());
        order.setUserId(request.getUserId());
        order.setOrderSource(request.getOrderSource().getCode());
        order.setTotalAmount(request.getTotalAmount());
        order.setPayAmount(request.getPayAmount());
        order.setRemark(request.getRemark());
        orderMapper.insert(order);
        return order.getId();
    }
}
