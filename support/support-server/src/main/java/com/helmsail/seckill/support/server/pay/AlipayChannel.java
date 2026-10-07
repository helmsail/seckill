package com.helmsail.seckill.support.server.pay;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import com.alipay.easysdk.payment.facetoface.Client;
import com.alipay.easysdk.payment.facetoface.models.AlipayTradePrecreateResponse;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.pay.PayChannelType;
import com.helmsail.seckill.support.api.pay.PayNotifyResult;
import com.helmsail.seckill.support.api.pay.PayRequest;
import com.helmsail.seckill.support.api.pay.PayTradeStatus;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

/**
 * 支付宝渠道（官方 SDK：com.alipay.sdk:alipay-easysdk，当面付 alipay.trade.precreate）
 *
 * 沙箱与生产共用同一个 SDK（不存在单独的"沙箱 SDK"），区别仅在网关地址与密钥——ALIPAY_GATEWAY_URL 缺省即沙箱网关。
 * 参数组装、RSA2 签名、HTTP 调用、回调验签等协议细节全部由 SDK 封装，本类只做适配器职责：
 *   preCreate：PayRequest → SDK 调用 → 二维码链接；
 *   verifyNotify：回调表单 → SDK 验签（本地公钥运算，不请求支付宝）→ PayNotifyResult。
 * 配置即启用：四要素（APP_ID / 应用私钥 / 支付宝公钥 / 异步通知地址）齐备才装配；未配置环境渠道不可用（工厂拒绝）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = {"ALIPAY_APP_ID", "ALIPAY_PRIVATE_KEY", "ALIPAY_PUBLIC_KEY", "ALIPAY_NOTIFY_URL"})
public class AlipayChannel implements PayChannel {

    /** 网关地址（缺省沙箱；生产改 https://openapi.alipay.com/gateway.do） */
    @Value("${ALIPAY_GATEWAY_URL:https://openapi-sandbox.dl.alipaydev.com/gateway.do}")
    private String gatewayUrl;

    /** 应用 APPID（沙箱：支付宝开放平台沙箱应用） */
    @Value("${ALIPAY_APP_ID}")
    private String appId;

    /** 应用私钥（PKCS8 Base64，可带 PEM 头尾，自动清洗） */
    @Value("${ALIPAY_PRIVATE_KEY}")
    private String privateKey;

    /** 支付宝公钥（验签异步通知） */
    @Value("${ALIPAY_PUBLIC_KEY}")
    private String alipayPublicKey;

    /** 异步通知地址（须公网可达；本地联调借助内网穿透） */
    @Value("${ALIPAY_NOTIFY_URL}")
    private String notifyUrl;

    /**
     * 初始化 SDK 全局配置（Factory 为 SDK 静态入口，进程内配置一次）
     */
    @PostConstruct
    void init() {
        URI uri = URI.create(gatewayUrl);
        Config config = new Config();
        config.protocol = uri.getScheme() == null ? "https" : uri.getScheme();
        config.gatewayHost = uri.getHost() == null ? gatewayUrl : uri.getHost();
        config.appId = appId;
        config.signType = "RSA2";
        // 密钥允许 PEM 头尾与换行，SDK 需要纯 Base64 段
        config.merchantPrivateKey = privateKey.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        config.alipayPublicKey = alipayPublicKey.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        config.notifyUrl = notifyUrl;
        Factory.setOptions(config);
        log.info("支付宝渠道初始化完成: gatewayHost={}, appId={}", config.gatewayHost, appId);
    }

    @Override
    public PayChannelType getChannelType() {
        return PayChannelType.ALIPAY;
    }

    /**
     * 预创建：SDK 调用 alipay.trade.precreate 生成收款二维码链接（前端可直接打开或渲染为二维码）
     */
    @Override
    public String preCreate(PayRequest request) {
        try {
            Client client = Factory.Payment.FaceToFace();
            if (request.getTimeoutMinutes() != null) {
                // 与订单关单时间对齐，缩小"关单后到账"窗口（超时后渠道侧不再允许支付）
                client.optional("timeout_express", request.getTimeoutMinutes() + "m");
            }
            AlipayTradePrecreateResponse response =
                    client.preCreate(request.getSubject(), request.getOutTradeNo(), request.getTotalAmount());
            if (!"10000".equals(response.getCode())) {
                log.error("支付宝预创建被拒: outTradeNo={}, subMsg={}", request.getOutTradeNo(), response.getSubMsg());
                throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(), "支付宝预创建失败: " + response.getSubMsg());
            }
            return response.getQrCode();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("支付宝预创建请求失败: outTradeNo={}", request.getOutTradeNo(), e);
            throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(), "支付宝预创建请求失败");
        }
    }

    /**
     * 回调：SDK 验签（支付宝公钥 RSA2）+ 字段解析与状态映射
     */
    @Override
    public PayNotifyResult verifyNotify(Map<String, String> params) {
        try {
            if (!Boolean.TRUE.equals(Factory.Payment.Common().verifyNotify(params))) {
                throw new BizException(ResultEnum.FORBIDDEN.getCode(), "支付宝回调验签失败");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("支付宝回调验签异常", e);
            throw new BizException(ResultEnum.FORBIDDEN.getCode(), "支付宝回调验签失败");
        }
        PayNotifyResult result = new PayNotifyResult();
        result.setValid(true);
        result.setOutTradeNo(params.get("out_trade_no"));
        result.setTradeNo(params.get("trade_no"));
        // 支付宝交易状态 → 统一内部状态
        String alipayStatus = params.get("trade_status") == null ? "" : params.get("trade_status");
        result.setTradeStatus(switch (alipayStatus) {
            case "TRADE_SUCCESS", "TRADE_FINISHED" -> PayTradeStatus.PAID;
            case "WAIT_BUYER_PAY" -> PayTradeStatus.PENDING;
            case "TRADE_CLOSED" -> PayTradeStatus.CLOSED;
            default -> throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "未知支付宝交易状态: " + alipayStatus);
        });
        result.setTotalAmount(params.get("total_amount"));
        return result;
    }
}
