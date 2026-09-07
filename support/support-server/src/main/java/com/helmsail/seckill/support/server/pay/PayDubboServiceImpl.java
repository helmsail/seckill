package com.helmsail.seckill.support.server.pay;

import com.helmsail.seckill.support.api.pay.PayService;
import com.helmsail.seckill.support.api.pay.PayRequest;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 支付 Dubbo 服务实现
 */
@DubboService
@RequiredArgsConstructor
public class PayDubboServiceImpl implements PayService {

    private final PayBizService payBizService;

    @Override
    public String preCreate(PayRequest request) {
        return payBizService.preCreate(request.getSubject(), request.getOutTradeNo(), request.getTotalAmount());
    }
}
