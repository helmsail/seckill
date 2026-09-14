package com.helmsail.seckill.support.server.pay;

import com.helmsail.seckill.support.api.pay.PayChannelType;
import com.helmsail.seckill.support.api.pay.PayGatewayDubboService;
import com.helmsail.seckill.support.api.pay.PayNotifyResult;
import com.helmsail.seckill.support.api.pay.PayRequest;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Map;

/**
 * 支付渠道网关 Dubbo 服务实现（统一入口）
 */
@DubboService
@RequiredArgsConstructor
public class PayGatewayDubboServiceImpl implements PayGatewayDubboService {

    private final PayChannelFactory payChannelFactory;

    @Override
    public String preCreate(PayChannelType channel, PayRequest request) {
        return payChannelFactory.getChannel(channel)
                .preCreate(request.getSubject(), request.getOutTradeNo(), request.getTotalAmount());
    }

    @Override
    public PayNotifyResult verifyNotify(PayChannelType channel, Map<String, String> params) {
        return payChannelFactory.getChannel(channel).verifyNotify(params);
    }
}
