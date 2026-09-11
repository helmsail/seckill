package com.helmsail.seckill.service.pay;

import com.helmsail.seckill.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
     * 支付回调（tradeNo 为第三方支付流水号，Mock 渠道可不传）
     */
    @PostMapping("/callback")
    public Result<Void> callback(@RequestParam String orderNo,
                                 @RequestParam(required = false) String tradeNo) {
        payService.payCallback(orderNo, tradeNo);
        return Result.success();
    }
}
