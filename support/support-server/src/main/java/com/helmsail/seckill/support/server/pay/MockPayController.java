package com.helmsail.seckill.support.server.pay;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟支付操作接口（演示/联调用）
 *
 * 模拟"用户完成付款但异步通知丢失"：仅修改渠道交易台账，不触发回调。
 * 调用后订单在渠道侧为已支付而 service 无感知，等待关单前查单补偿纠正。
 *
 * 注意：演示专用接口，生产环境不应暴露。
 */
@RestController
@RequestMapping("/mock/pay")
@RequiredArgsConstructor
public class MockPayController {

    private final MockChannel mockChannel;

    /**
     * 标记订单在渠道侧已支付（不发通知）
     */
    @PostMapping("/mark-paid/{outTradeNo}")
    public String markPaid(@PathVariable String outTradeNo) {
        mockChannel.markPaid(outTradeNo);
        return "ok";
    }
}
