package com.helmsail.seckill.base.order;

import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.PageResult;

import java.util.List;

/**
 * 秒杀订单 Dubbo 服务接口
 */
public interface SeckillOrderService {

    /**
     * 创建订单（订单号自动生成）
     */
    String createOrder(CreateSeckillOrderRequest request) throws BizException;

    /**
     * 使用订单号查找订单
     */
    SeckillOrderDTO getByOrderNo(String orderNo) throws BizException;

    /**
     * 使用用户 ID 查找订单（分页）
     */
    PageResult<SeckillOrderDTO> pageByUserId(SeckillOrderPageQuery query) throws BizException;

    /**
     * 订单支付成功
     *
     * @param orderNo 订单号
     * @param tradeNo 第三方支付流水号
     */
    void paySuccess(String orderNo, String tradeNo) throws BizException;

    /**
     * 关闭订单（仅待支付可关闭）
     *
     * @return true=本次调用完成关闭（可据此回补库存/限购）；false=状态已变更，无需处理
     */
    boolean closeOrder(String orderNo) throws BizException;

    /**
     * 查询超时未支付的订单号（兜底补关单）
     *
     * @param beforeMinutes 创建时间早于 now - beforeMinutes
     * @param limit         单次上限（分片表按分片生效，总量为分片数 × limit）
     */
    List<String> listTimeoutOrderNos(int beforeMinutes, int limit) throws BizException;

    /**
     * 查询指定时间窗内已支付的订单（订单同步对账用）
     *
     * @param minutesAgo 支付时间在 now - minutesAgo 之后
     * @param limit      单次上限（分片表按分片生效，总量为分片数 × limit）
     */
    List<SeckillOrderDTO> listPaidOrdersSince(int minutesAgo, int limit) throws BizException;
}
