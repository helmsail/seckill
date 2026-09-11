package com.helmsail.seckill.service.order;

import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderPageQuery;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.PageResult;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.common.tracing.UserContext;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * C 端订单 Controller（我的订单）
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    @DubboReference
    private SeckillOrderService seckillOrderService;

    /**
     * 我的订单（分页，按创建时间倒序）
     *
     * userId 由认证上下文注入并覆盖入参，防越权查询他人订单
     */
    @GetMapping("/list")
    public Result<PageResult<SeckillOrderDTO>> list(SeckillOrderPageQuery query) {
        query.setUserId(requireUserId());
        return Result.success(seckillOrderService.pageByUserId(query));
    }

    /**
     * 订单详情（仅本人可见）
     */
    @GetMapping("/{orderNo}")
    public Result<SeckillOrderDTO> detail(@PathVariable String orderNo) {
        Long userId = requireUserId();
        SeckillOrderDTO order = seckillOrderService.getByOrderNo(orderNo);
        if (!userId.equals(order.getUserId())) {
            throw new BizException(ResultEnum.FORBIDDEN);
        }
        return Result.success(order);
    }

    /**
     * 取当前登录用户 ID（上下文缺失按未登录处理，避免 NumberFormatException 被兜为 500）
     */
    private Long requireUserId() {
        String userId = UserContext.currentUserId();
        if (userId == null || userId.isBlank() || "N/A".equals(userId)) {
            throw new BizException(ResultEnum.FORBIDDEN.getCode(), "用户未登录");
        }
        return Long.valueOf(userId);
    }
}
