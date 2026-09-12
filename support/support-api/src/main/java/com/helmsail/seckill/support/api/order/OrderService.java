package com.helmsail.seckill.support.api.order;

import com.helmsail.seckill.common.exception.BizException;

import java.util.List;

/**
 * 订单 Dubbo 服务接口
 */
public interface OrderService {

    /**
     * 创建订单（按 orderNo 幂等：重复创建返回已有订单）
     */
    Long create(CreateOrderRequest request) throws BizException;

    /**
     * 批量查询已存在的订单号（秒杀域订单同步对账用）
     */
    List<String> listExistingOrderNos(List<String> orderNos) throws BizException;
}
