package com.helmsail.seckill.support.server.pay;

import com.helmsail.seckill.support.api.pay.PayChannelType;
import com.helmsail.seckill.support.api.pay.PayNotifyResult;
import com.helmsail.seckill.support.api.pay.PayTradeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 模拟支付渠道（本地开发，无状态）
 *
 * 无真实支付网关地址：预创建直接返回"模拟支付操作说明"——
 * 按说明调用回调接口即可模拟支付成功，不操作则订单超时关闭模拟支付失败；
 * 前端以查询接口轮询最终结果。不需要任何渠道台账或外部系统。
 */
@Slf4j
@Component
public class MockChannel implements PayChannel {

    private static final String TRADE_NO_PREFIX = "MOCK";

    /** 回调接口地址（seckill-service 收银台），由 .env 注入 */
    @Value("${MOCK_PAY_CALLBACK_URL}")
    private String callbackUrl;

    /** 支付结果查询接口地址（seckill-service 订单域），由 .env 注入 */
    @Value("${MOCK_PAY_STATUS_URL}")
    private String statusUrl;

    @Override
    public PayChannelType getChannelType() {
        return PayChannelType.MOCK;
    }

    /**
     * 预创建：模拟渠道无真实网关地址，直接返回操作说明（见类注释）
     */
    @Override
    public String preCreate(String subject, String outTradeNo, String totalAmount) {
        log.info("【模拟支付】预创建（返回操作说明）: outTradeNo={}, subject={}, amount={}",
                outTradeNo, subject, totalAmount);
        return """
                【模拟支付说明】本渠道为本地模拟，无需真实扫码，按以下方式操作：
                1. 模拟支付成功：GET %s?out_trade_no={订单号}&trade_status=PAID&total_amount={金额}
                   浏览器直接访问即可，订单随即转为已支付；total_amount 须与订单应付金额一致
                2. 模拟支付失败：无需任何操作，订单保持待支付，超时后自动关闭
                3. 前端获取最终结果：轮询 GET %s?orderNo={订单号}
                   PENDING 继续轮询 / PAID 跳成功页 / CLOSED 跳失败页\
                """.formatted(callbackUrl, statusUrl);
    }

    /**
     * 验签：模拟渠道恒通过，按标准字段解析回调参数（回调到达即视为完成付款）
     */
    @Override
    public PayNotifyResult verifyNotify(Map<String, String> params) {
        PayNotifyResult result = new PayNotifyResult();
        result.setValid(true);
        result.setOutTradeNo(params.get("out_trade_no"));
        result.setTradeNo(params.getOrDefault("trade_no", TRADE_NO_PREFIX + params.get("out_trade_no")));
        result.setTradeStatus(params.getOrDefault("trade_status", PayTradeStatus.PAID));
        result.setTotalAmount(params.get("total_amount"));
        log.info("【模拟支付】验签通过（模拟）: outTradeNo={}, tradeStatus={}",
                result.getOutTradeNo(), result.getTradeStatus());
        return result;
    }
}
