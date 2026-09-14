package com.helmsail.seckill.service.order;

import com.helmsail.seckill.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C 端订单 Controller（订单状态查询）
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderQueryService orderQueryService;

    /**
     * 查询订单状态
     *
     * DB 为权威事实源：状态由回调写路径推进，本接口只读返回。
     */
    @GetMapping("/status")
    public Result<OrderStatusVO> queryStatus(@RequestParam String orderNo) {
        return Result.success(orderQueryService.queryStatus(orderNo));
    }
}
