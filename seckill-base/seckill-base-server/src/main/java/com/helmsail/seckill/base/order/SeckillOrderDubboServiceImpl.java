package com.helmsail.seckill.base.order;

import com.helmsail.seckill.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 秒杀订单 Dubbo 服务实现
 *
 * retries = 0：本服务含非幂等写操作（创建订单），自动重试会造成重复下单
 */
@DubboService(retries = 0)
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
    public PageResult<SeckillOrderDTO> pageByUserId(SeckillOrderPageQuery query) {
        return seckillOrderBizService.pageByUserId(query);
    }

    @Override
    public void paySuccess(String orderNo, String tradeNo) {
        seckillOrderBizService.paySuccess(orderNo, tradeNo);
    }

    @Override
    public void closeOrder(String orderNo) {
        seckillOrderBizService.closeOrder(orderNo);
    }
}
