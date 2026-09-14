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
     * Mock 场景：按 preCreate 返回的说明以 GET 访问本接口即模拟支付成功；
     * 真实渠道为 POST 异步通知，字段以 verifyNotify 实现为准（生产环境应仅保留 POST 并强制验签）。
     */
    @RequestMapping(value = "/callback", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<Void> callback(@RequestParam Map<String, String> params) {
        payService.payCallback(params);
        return Result.success();
    }
}
