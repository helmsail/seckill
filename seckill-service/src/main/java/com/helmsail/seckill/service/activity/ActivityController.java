package com.helmsail.seckill.service.activity;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.base.seckill.SeckillResultVO;
import com.helmsail.seckill.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C 端活动 Controller：活动查询与秒杀提交统一入口
 *
 * 查询走 ActivityQueryService（Caffeine → Redis 快照 → Dubbo 回源）；
 * 秒杀提交/结果轮询走 SeckillService（准入检查 → MQ 异步下单 → 结果键轮询）。
 * URL 保持原路径（/activity 与 /seckill 两个前缀），前端无需改动。
 */
@RestController
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityQueryService activityQueryService;
    private final SeckillService seckillService;

    // ==================== 活动查询 ====================

    /**
     * 查询活动列表（从 Redis Hash 获取，保底走 Dubbo）
     */
    @GetMapping("/activity/list")
    public Result<List<ActivityDTO>> list() {
        return Result.success(activityQueryService.listActivities());
    }

    /**
     * 查询活动信息
     */
    @GetMapping("/activity/{activityNo}")
    public Result<ActivityDTO> getByActivityNo(@PathVariable String activityNo) {
        return Result.success(activityQueryService.getActivityByNo(activityNo));
    }

    /**
     * 查询活动商品SKU列表
     */
    @GetMapping("/activity/{activityNo}/products")
    public Result<List<ActivityProductVO>> getProductList(@PathVariable String activityNo) {
        return Result.success(activityQueryService.getSkuListByActivityNo(activityNo));
    }

    /**
     * 查询 SKU 库存（按活动 + SKU 定位）
     */
    @GetMapping("/activity/{activityNo}/sku/{skuNo}/stock")
    public Result<Integer> getSkuStock(@PathVariable String activityNo, @PathVariable String skuNo) {
        return Result.success(activityQueryService.getSkuStock(activityNo, skuNo));
    }

    // ==================== 秒杀提交 ====================

    /**
     * 秒杀提交（返回 traceId，结果经 /seckill/poll 轮询）
     */
    @PostMapping("/seckill")
    public Result<String> seckill(@Valid @RequestBody SeckillRequest request) {
        return Result.success(seckillService.executeSeckill(request));
    }

    /**
     * 秒杀结果轮询
     */
    @GetMapping("/seckill/poll")
    public Result<SeckillResultVO> poll(@RequestParam String traceId) {
        return Result.success(seckillService.pollResult(traceId));
    }
}
