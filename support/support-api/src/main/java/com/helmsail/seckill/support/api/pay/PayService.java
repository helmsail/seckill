package com.helmsail.seckill.support.api.pay;

/**
 * 支付 Dubbo 服务接口
 */
public interface PayService {

    /**
     * 创建当面付收款二维码
     *
     * @param request 支付请求
     * @return 二维码链接
     */
    String preCreate(PayRequest request);
}
