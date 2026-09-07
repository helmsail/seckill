package com.helmsail.seckill.base.order;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 秒杀订单 Dubbo 服务实现
 */
@DubboService
@RequiredArgsConstructor
public class SeckillOrderDubboServiceImpl implements SeckillOrderService {

    private final SeckillOrderBizService seckillOrderBizService;

    @Override
    public String createOrder(CreateSeckillOrderRequest request) {
        return seckillOrderBizService.createOrder(request);
    }

    @Override
    public SeckillOrderDTO getByOrderNo(String orderNo) {
        return seckillOrderBizService.getByOrderNo(orderNo);
    }

    @Override
    public SeckillOrderPageResult pageByUserId(SeckillOrderPageQuery query) {
        return seckillOrderBizService.pageByUserId(query);
    }

    @Override
    public void paySuccess(String orderNo) {
        seckillOrderBizService.paySuccess(orderNo);
    }

    @Override
    public void closeOrder(String orderNo) {
        seckillOrderBizService.closeOrder(orderNo);
    }
}
