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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟支付渠道（本地开发）
 *
 * 不调用任何外部支付系统，用于本地快速验证完整支付链路：
 * - 预创建：返回模拟二维码链接，登记交易台账（待支付）
 * - 验签：直接视为验签通过，按标准字段解析参数（回调到达即视为用户完成付款）
 * - 查询：按台账返回真实状态（PAID/PENDING），支持"回调丢失"场景演练
 */
@Slf4j
@Component
public class MockChannel implements PayChannel {

    private static final String MOCK_TRADE_NO_PREFIX = "MOCK";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 交易台账：outTradeNo → 是否已支付（演示用途，单实例内存态，重启即失） */
    private final Map<String, Boolean> paidLedger = new ConcurrentHashMap<>();

    @Override
    public PayChannelType getChannelType() {
        return PayChannelType.MOCK;
    }

    @Override
    public String preCreate(String subject, String outTradeNo, String totalAmount) {
        log.info("【模拟支付】预创建二维码: outTradeNo={}, subject={}, amount={}", outTradeNo, subject, totalAmount);
        paidLedger.putIfAbsent(outTradeNo, Boolean.FALSE);
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
        if (outTradeNo != null) {
            // 回调到达即用户完成付款，登记台账
            paidLedger.put(outTradeNo, Boolean.TRUE);
        }
        log.info("【模拟支付】验签通过: outTradeNo={}, tradeStatus={}", result.getOutTradeNo(), result.getTradeStatus());
        return result;
    }

    @Override
    public PayTradeResult queryTrade(String outTradeNo) {
        PayTradeResult result = new PayTradeResult();
        result.setOutTradeNo(outTradeNo);
        boolean paid = Boolean.TRUE.equals(paidLedger.get(outTradeNo));
        if (paid) {
            result.setTradeNo(MOCK_TRADE_NO_PREFIX + outTradeNo);
            result.setPayTime(LocalDateTime.now().format(DATE_TIME));
        }
        result.setTradeStatus(paid ? PayTradeStatus.PAID : PayTradeStatus.PENDING);
        log.info("【模拟支付】查询交易: outTradeNo={} → {}", outTradeNo, result.getTradeStatus());
        return result;
    }

    /**
     * 模拟用户完成付款（只改台账、不产生异步通知）
     *
     * 用于演练"回调丢失"：渠道侧已支付而 service 无感知，等待查单补偿纠正。
     */
    public void markPaid(String outTradeNo) {
        paidLedger.put(outTradeNo, Boolean.TRUE);
        log.info("【模拟支付】手动标记已支付（不发通知）: outTradeNo={}", outTradeNo);
    }
}
