package com.helmsail.seckill.support.server.pay;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.factory.Factory.Payment;
import com.alipay.easysdk.kernel.Config;
import com.alipay.easysdk.kernel.util.ResponseChecker;
import com.alipay.easysdk.payment.facetoface.models.AlipayTradePrecreateResponse;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 支付服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayBizService {

    private final AlipayProperties alipayProperties;

    @PostConstruct
    public void init() {
        Factory.setOptions(toConfig());
        log.info("支付宝 SDK 初始化完成");
    }

    /**
     * 创建当面付收款二维码
     *
     * @param subject  商品标题
     * @param outTradeNo  商户订单号
     * @param totalAmount  金额（单位：元）
     * @return 二维码链接
     */
    public String preCreate(String subject, String outTradeNo, String totalAmount) {
        try {
            AlipayTradePrecreateResponse response = Payment.FaceToFace()
                    .preCreate(subject, outTradeNo, totalAmount);

            if (ResponseChecker.success(response)) {
                log.info("支付宝预创建成功: outTradeNo={}, qrCode={}", outTradeNo, response.qrCode);
                return response.qrCode;
            } else {
                log.error("支付宝预创建失败: outTradeNo={}, code={}, msg={}",
                        outTradeNo, response.code, response.msg);
                throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(), response.msg);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("支付宝调用异常: outTradeNo={}", outTradeNo, e);
            throw new BizException(ResultEnum.SYSTEM_ERROR);
        }
    }

    private Config toConfig() {
        Config config = new Config();
        config.protocol = alipayProperties.getProtocol();
        config.gatewayHost = alipayProperties.getGatewayHost();
        config.signType = alipayProperties.getSignType();
        config.appId = alipayProperties.getAppId();
        config.merchantPrivateKey = alipayProperties.getMerchantPrivateKey();
        config.merchantCertPath = alipayProperties.getMerchantCertPath();
        config.alipayCertPath = alipayProperties.getAlipayCertPath();
        config.alipayRootCertPath = alipayProperties.getAlipayRootCertPath();
        config.notifyUrl = alipayProperties.getNotifyUrl();
        config.encryptKey = alipayProperties.getEncryptKey();
        return config;
    }
}
