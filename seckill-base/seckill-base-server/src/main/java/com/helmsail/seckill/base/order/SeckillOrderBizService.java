package com.helmsail.seckill.base.order;

import com.helmsail.seckill.common.result.PageResult;

import java.util.List;

/**
 * 秒杀订单内部服务接口
 */
public interface SeckillOrderBizService {

    String createOrder(CreateSeckillOrderRequest request);

    SeckillOrderDTO getByOrderNo(String orderNo);

    PageResult<SeckillOrderDTO> pageByUserId(SeckillOrderPageQuery query);

    void paySuccess(String orderNo, String tradeNo);

    boolean closeOrder(String orderNo);

    List<String> listTimeoutOrderNos(int beforeMinutes, int limit);

    List<SeckillOrderDTO> listPaidOrdersSince(int minutesAgo, int limit);
}
