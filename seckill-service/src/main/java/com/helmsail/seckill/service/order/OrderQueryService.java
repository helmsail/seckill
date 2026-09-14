package com.helmsail.seckill.service.order;

import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderDubboService;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.common.tracing.UserContext;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

/**
 * C 端订单查询服务（纯读，不感知支付渠道）
 *
 * 只读本地订单表：渠道侧支付完成后才异步回调本系统，状态由回调写路径推进
 * （见 PayService.payCallback），故查询无需与渠道交互。
 */
@Service
public class OrderQueryService {

    @DubboReference
    private SeckillOrderDubboService seckillOrderService;

    /**
     * 查询订单状态
     *
     * 如实返回订单当前状态（PENDING/PAID/CLOSED），以本地订单表为准。
     */
    public OrderStatusVO queryStatus(String orderNo) {
        // 1. 查询订单（不存在由 base 抛 ORDER_NOT_FOUND）
        SeckillOrderDTO order = seckillOrderService.getByOrderNo(orderNo);

        // 2. 归属校验：仅订单本人可查询（防越权）
        if (!String.valueOf(order.getUserId()).equals(UserContext.currentUserId())) {
            throw new BizException(ResultEnum.FORBIDDEN);
        }

        // 3. 组装返回（以本地订单状态为准）
        OrderStatusVO vo = new OrderStatusVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getOrderStatus().name());
        vo.setPaidTime(order.getPaidTime());
        return vo;
    }
}
