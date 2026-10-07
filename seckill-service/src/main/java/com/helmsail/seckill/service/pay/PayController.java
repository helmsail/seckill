package com.helmsail.seckill.service.pay;

import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.pay.PayChannelType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;

    /**
     * 预支付：获取支付二维码（渠道：mock 模拟 / alipay 支付宝沙箱）
     */
    @PostMapping("/prepay")
    public Result<String> prePay(@RequestParam String orderNo, @RequestParam String channel) {
        return Result.success(payService.prePay(orderNo, PayChannelType.byCode(channel)));
    }

    /**
     * 支付回调（渠道异步通知，参数为渠道原始字段；路径携带渠道路由）
     *
     * 形态与真实渠道一致：POST + form 参数，唯一区别是触发者——
     * 真实渠道为第三方服务器在用户支付后发起；Mock 为前端"模拟支付成功"按钮扮演第三方发起。
     */
    @PostMapping("/callback/{channel}")
    public Result<Void> callback(@PathVariable String channel, @RequestParam Map<String, String> params) {
        payService.payCallback(PayChannelType.byCode(channel), params);
        return Result.success();
    }
}
