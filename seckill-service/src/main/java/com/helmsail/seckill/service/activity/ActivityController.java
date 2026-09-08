package com.helmsail.seckill.service.activity;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C 端活动查询 Controller
 */
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityQueryService activityQueryService;

    /**
     * 查询活动列表（从 Redis Hash 获取，保底走 Dubbo）
     */
    @GetMapping("/list")
    public Result<List<ActivityDTO>> list() {
        return Result.success(activityQueryService.listActivities());
    }

    /**
     * 查询活动信息
     */
    @GetMapping("/{activityNo}")
    public Result<ActivityDTO> getByActivityNo(@PathVariable String activityNo) {
        return Result.success(activityQueryService.getActivityByNo(activityNo));
    }

    /**
     * 查询活动商品+SKU列表
     */
    @GetMapping("/{activityNo}/products")
    public Result<List<Map<String, Object>>> getProductList(@PathVariable String activityNo) {
        return Result.success(activityQueryService.getProductListByActivityNo(activityNo));
    }

    /**
     * 查询 SKU 库存
     */
    @GetMapping("/sku/{skuNo}/stock")
    public Result<Integer> getSkuStock(@PathVariable String skuNo) {
        return Result.success(activityQueryService.getSkuStock(skuNo));
    }
}
