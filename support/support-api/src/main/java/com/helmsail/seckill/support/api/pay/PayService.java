package com.helmsail.seckill.support.api.pay;

import java.util.Map;

/**
 * 支付 Dubbo 服务接口（统一入口）
 *
 * 所有支付能力按渠道类型统一分发。
 */
public interface PayService {

    /**
     * 创建收款二维码
     *
     * @param channel 支付渠道
     * @param request 支付请求
     * @return 二维码链接
     */
    String preCreate(PayChannelType channel, PayRequest request);

    /**
     * 验签并解析异步通知（仅技术验证，不做业务判断）
     *
     * @param channel 支付渠道
     * @param params  异步通知的原始参数
     * @return 验签与解析结果
     */
    PayNotifyResult verifyNotify(PayChannelType channel, Map<String, String> params);

    /**
     * 主动查询交易状态
     *
     * @param channel    支付渠道
     * @param outTradeNo 商户订单号
     * @return 交易查询结果
     */
    PayTradeResult queryTrade(PayChannelType channel, String outTradeNo);
}
