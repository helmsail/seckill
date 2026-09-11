package com.helmsail.seckill.support.server.pay;

import com.helmsail.seckill.support.api.pay.PayChannelType;
import com.helmsail.seckill.support.api.pay.PayNotifyResult;
import com.helmsail.seckill.support.api.pay.PayTradeResult;
import com.helmsail.seckill.support.api.pay.PayTradeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 模拟支付渠道（本地开发）
 *
 * 不调用任何外部支付系统，用于本地快速验证完整支付链路：
 * - 预创建：返回模拟二维码链接
 * - 验签：直接视为验签通过，按标准字段解析参数
 * - 查询：一律返回已支付（模拟支付成功）
 */
@Slf4j
@Component
public class MockChannel implements PayChannel {

    private static final String MOCK_TRADE_NO_PREFIX = "MOCK";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PayChannelType getChannelType() {
        return PayChannelType.MOCK;
    }

    @Override
    public String preCreate(String subject, String outTradeNo, String totalAmount) {
        log.info("【模拟支付】预创建二维码: outTradeNo={}, subject={}, amount={}", outTradeNo, subject, totalAmount);
        return "https://mock.pay/qr/" + outTradeNo;
    }

    @Override
    public PayNotifyResult verifyNotify(Map<String, String> params) {
        String outTradeNo = params.get("out_trade_no");
        PayNotifyResult result = new PayNotifyResult();
        result.setValid(true);
        result.setOutTradeNo(outTradeNo);
        result.setTradeNo(params.getOrDefault("trade_no", MOCK_TRADE_NO_PREFIX + outTradeNo));
        result.setTradeStatus(params.getOrDefault("trade_status", PayTradeStatus.PAID));
        result.setTotalAmount(params.get("total_amount"));
        log.info("【模拟支付】验签通过: outTradeNo={}, tradeStatus={}", result.getOutTradeNo(), result.getTradeStatus());
        return result;
    }

    @Override
    public PayTradeResult queryTrade(String outTradeNo) {
        PayTradeResult result = new PayTradeResult();
        result.setOutTradeNo(outTradeNo);
        result.setTradeNo(MOCK_TRADE_NO_PREFIX + outTradeNo);
        result.setTradeStatus(PayTradeStatus.PAID);
        result.setPayTime(LocalDateTime.now().format(DATE_TIME));
        log.info("【模拟支付】查询交易: outTradeNo={} → {}", outTradeNo, PayTradeStatus.PAID);
        return result;
    }
}
