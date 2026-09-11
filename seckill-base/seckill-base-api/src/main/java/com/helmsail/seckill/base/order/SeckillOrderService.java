package com.helmsail.seckill.base.order;

import com.helmsail.seckill.common.result.PageResult;

/**
 * 秒杀订单 Dubbo 服务接口
 */
public interface SeckillOrderService {

    /**
     * 创建订单（订单号自动生成）
     */
    String createOrder(CreateSeckillOrderRequest request);

    /**
     * 使用订单号查找订单
     */
    SeckillOrderDTO getByOrderNo(String orderNo);

    /**
     * 使用用户 ID 查找订单（分页）
     */
    PageResult<SeckillOrderDTO> pageByUserId(SeckillOrderPageQuery query);

    /**
     * 订单支付成功
     */
    void paySuccess(String orderNo);

    /**
     * 关闭订单
     */
    void closeOrder(String orderNo);
}
