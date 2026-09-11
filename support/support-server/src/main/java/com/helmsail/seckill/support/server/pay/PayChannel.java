package com.helmsail.seckill.support.server.pay;

import com.helmsail.seckill.support.api.pay.PayChannelType;
import com.helmsail.seckill.support.api.pay.PayNotifyResult;
import com.helmsail.seckill.support.api.pay.PayTradeResult;

import java.util.Map;

/**
 * 支付渠道
 *
 * 各渠道实现自身的支付能力，由 PayChannelRouter 统一分发。
 */
public interface PayChannel {

    /**
     * 渠道类型
     */
    PayChannelType getChannelType();

    /**
     * 创建收款二维码
     *
     * @param subject     商品标题
     * @param outTradeNo  商户订单号
     * @param totalAmount 金额（单位：元）
     * @return 二维码链接
     */
    String preCreate(String subject, String outTradeNo, String totalAmount);

    /**
     * 验签并解析异步通知（仅技术验证，不做业务判断）
     *
     * @param params 异步通知的原始参数
     * @return 验签与解析结果
     */
    PayNotifyResult verifyNotify(Map<String, String> params);

    /**
     * 主动查询交易状态
     *
     * @param outTradeNo 商户订单号
     * @return 交易查询结果
     */
    PayTradeResult queryTrade(String outTradeNo);
}
