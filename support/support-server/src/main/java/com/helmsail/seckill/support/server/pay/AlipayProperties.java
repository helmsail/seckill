package com.helmsail.seckill.support.server.pay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 协议 */
    private String protocol = "https";

    /** 网关地址 */
    private String gatewayHost = "openapi.alipay.com";

    /** 签名方式 */
    private String signType = "RSA2";

    /** 应用ID */
    private String appId;

    /** 应用私钥 */
    private String merchantPrivateKey;

    /** 应用公钥证书路径 */
    private String merchantCertPath;

    /** 支付宝公钥证书路径 */
    private String alipayCertPath;

    /** 支付宝根证书路径 */
    private String alipayRootCertPath;

    /** 异步通知地址 */
    private String notifyUrl;

    /** AES密钥 */
    private String encryptKey;
}
