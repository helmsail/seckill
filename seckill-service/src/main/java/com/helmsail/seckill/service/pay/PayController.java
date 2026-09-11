package com.helmsail.seckill.service.pay;

import com.helmsail.seckill.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /**
     * 预支付：获取支付二维码
     */
    @PostMapping("/prepay")
    public Result<String> prePay(@RequestParam String orderNo) {
        return Result.success(payService.prePay(orderNo));
    }

    /**
     * 支付回调（渠道异步通知，参数为渠道原始字段）
     *
     * Mock 渠道可不传 trade_no/total_amount；真实渠道字段以 verifyNotify 实现为准。
     */
    @PostMapping("/callback")
    public Result<Void> callback(@RequestParam Map<String, String> params) {
        payService.payCallback(params);
        return Result.success();
    }
}
