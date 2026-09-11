package com.helmsail.seckill.support.server.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helmsail.seckill.support.api.order.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单服务实现
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderBizService {

    private final OrderMapper orderMapper;

    @Override
    public Long create(CreateOrderRequest request) {
        // 幂等：同 orderNo 重复创建（MQ 重复消费/重试）直接返回已有订单
        Order existing = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, request.getOrderNo()));
        if (existing != null) {
            return existing.getId();
        }
        Order order = new Order();
        order.setOrderNo(request.getOrderNo());
        order.setUserId(request.getUserId());
        order.setOrderSource(request.getOrderSource().getCode());
        order.setTotalAmount(request.getTotalAmount());
        order.setPayAmount(request.getPayAmount());
        order.setPaidTime(request.getPaidTime());
        order.setTradeNo(request.getTradeNo());
        orderMapper.insert(order);
        return order.getId();
    }

    @Override
    public List<String> listExistingOrderNos(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return List.of();
        }
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .in(Order::getOrderNo, orderNos))
                .stream().map(Order::getOrderNo).toList();
    }
}
