package com.helmsail.seckill.support.api.pay;

import com.helmsail.seckill.common.exception.BizException;

import java.util.Map;

/**
 * 支付渠道网关 Dubbo 接口（统一入口）
 *
 * 所有支付能力按渠道类型统一分发（预创建 / 验签解析）。
 * 职责边界：只与渠道交互，不做业务编排（订单状态流转等由业务域收银台服务负责），故名"网关"。
 */
public interface PayGatewayDubboService {

    /**
     * 创建收款二维码
     *
     * @param channel 支付渠道
     * @param request 支付请求
     * @return 二维码链接
     */
    String preCreate(PayChannelType channel, PayRequest request) throws BizException;

    /**
     * 验签并解析异步通知（仅技术验证，不做业务判断）
     *
     * @param channel 支付渠道
     * @param params  异步通知的原始参数
     * @return 验签与解析结果
     */
    PayNotifyResult verifyNotify(PayChannelType channel, Map<String, String> params) throws BizException;
}
