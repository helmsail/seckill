package com.helmsail.seckill.support.server.pay;

import com.helmsail.seckill.support.api.pay.PayChannelType;
import com.helmsail.seckill.support.api.pay.PayNotifyResult;
import com.helmsail.seckill.support.api.pay.PayRequest;
import com.helmsail.seckill.support.api.pay.PayService;
import com.helmsail.seckill.support.api.pay.PayTradeResult;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Map;

/**
 * 支付 Dubbo 服务实现（统一入口）
 */
@DubboService
@RequiredArgsConstructor
public class PayDubboServiceImpl implements PayService {

    private final PayChannelRouter payChannelRouter;

    @Override
    public String preCreate(PayChannelType channel, PayRequest request) {
        return payChannelRouter.route(channel)
                .preCreate(request.getSubject(), request.getOutTradeNo(), request.getTotalAmount());
    }

    @Override
    public PayNotifyResult verifyNotify(PayChannelType channel, Map<String, String> params) {
        return payChannelRouter.route(channel).verifyNotify(params);
    }

    @Override
    public PayTradeResult queryTrade(PayChannelType channel, String outTradeNo) {
        return payChannelRouter.route(channel).queryTrade(outTradeNo);
    }
}
