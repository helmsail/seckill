package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.admin.vo.ActivityDetailVO;
import com.helmsail.seckill.base.activity.*;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDubboService;
import com.helmsail.seckill.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.Method;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 活动管理 Controller
 */
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {

    /** 仅 create 禁重试（不幂等：重试 = 重复创建）；其余方法效果幂等/CAS 安全，允许默认重试 */
    @DubboReference(methods = @Method(name = "create", retries = 0))
    private ActivityDubboService activityService;

    @DubboReference
    private SeckillProductSkuDubboService seckillProductSkuService;

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
     * 查询活动详情（活动 + 秒杀域 SKU 平铺列表；SPU 分组塑形由前端完成）
     */
    @GetMapping("/{activityNo}/detail")
    public Result<ActivityDetailVO> detail(@PathVariable String activityNo) {
        ActivityDTO activity = activityService.getByActivityNo(activityNo);
        List<SeckillProductSkuDTO> skus = seckillProductSkuService.listByActivityNo(activityNo);
        return Result.success(new ActivityDetailVO(activity, skus));
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
