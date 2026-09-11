package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.admin.vo.ActivityDetailVO;
import com.helmsail.seckill.base.activity.*;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 活动管理 Controller
 */
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    /**
     * 创建活动
     */
    @PostMapping
    public Result<String> create(@Valid @RequestBody ActivityRequest request) {
        return Result.success(activityService.create(request));
    }

    /**
     * 删除活动（仅待开始状态）
     */
    @DeleteMapping("/{activityNo}")
    public Result<Void> delete(@PathVariable String activityNo) {
        activityService.delete(activityNo);
        return Result.success();
    }

    /**
     * 修改活动（仅待开始状态）
     */
    @PutMapping("/{activityNo}")
    public Result<Void> update(@PathVariable String activityNo, @Valid @RequestBody ActivityRequest request) {
        activityService.update(activityNo, request);
        return Result.success();
    }

    /**
     * 查询单个活动
     */
    @GetMapping("/{activityNo}")
    public Result<ActivityDTO> getByActivityNo(@PathVariable String activityNo) {
        return Result.success(activityService.getByActivityNo(activityNo));
    }

    /**
     * 查询活动详情（活动 + 按 SPU 分组的商品SKU，编排在本层）
     */
    @GetMapping("/{activityNo}/detail")
    public Result<ActivityDetailVO> detail(@PathVariable String activityNo) {
        ActivityDTO activity = activityService.getByActivityNo(activityNo);
        List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);

        Map<String, List<SeckillProductSkuDTO>> grouped = rows.stream()
                .collect(Collectors.groupingBy(SeckillProductSkuDTO::getSpuNo,
                        LinkedHashMap::new, Collectors.toList()));

        List<ActivityDetailVO.ProductWithSkus> productWithSkusList = new ArrayList<>();
        for (Map.Entry<String, List<SeckillProductSkuDTO>> entry : grouped.entrySet()) {
            SeckillProductSkuDTO first = entry.getValue().get(0);
            ActivityDetailVO.ProductInfo productInfo = new ActivityDetailVO.ProductInfo(
                    activityNo, first.getSpuNo(), first.getSpuName(),
                    first.getDiscountType().name(), first.getDiscountParameter());
            productWithSkusList.add(new ActivityDetailVO.ProductWithSkus(productInfo, entry.getValue()));
        }
        return Result.success(new ActivityDetailVO(activity, productWithSkusList));
    }

    /**
     * 查询活动列表（status 为空时全量，否则按状态过滤）
     */
    @GetMapping("/list")
    public Result<List<ActivityDTO>> list(@RequestParam(required = false) ActivityStatus status) {
        if (status == null) {
            return Result.success(activityService.listAll());
        }
        return Result.success(activityService.listByStatus(status));
    }

    /**
     * 暂停活动（进行中 → 已暂停）
     */
    @PutMapping("/{activityNo}/pause")
    public Result<Void> pause(@PathVariable String activityNo) {
        activityService.pause(activityNo);
        return Result.success();
    }

    /**
     * 继续活动（已暂停 → 进行中）
     */
    @PutMapping("/{activityNo}/resume")
    public Result<Void> resume(@PathVariable String activityNo) {
        activityService.resume(activityNo);
        return Result.success();
    }

    /**
     * 关闭活动（进行中/已暂停 → 已关闭）
     */
    @PutMapping("/{activityNo}/close")
    public Result<Void> close(@PathVariable String activityNo) {
        activityService.close(activityNo);
        return Result.success();
    }
}
