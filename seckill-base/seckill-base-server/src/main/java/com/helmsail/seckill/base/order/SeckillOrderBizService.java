package com.helmsail.seckill.base.order;

import com.helmsail.seckill.common.result.PageResult;

/**
 * 秒杀订单内部服务接口
 */
public interface SeckillOrderBizService {

    String createOrder(CreateSeckillOrderRequest request);

    SeckillOrderDTO getByOrderNo(String orderNo);

    PageResult<SeckillOrderDTO> pageByUserId(SeckillOrderPageQuery query);

    void paySuccess(String orderNo, String tradeNo);

    void closeOrder(String orderNo);
}
