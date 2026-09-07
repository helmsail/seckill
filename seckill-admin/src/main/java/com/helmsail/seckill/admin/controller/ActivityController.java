package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.base.activity.*;
import com.helmsail.seckill.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 活动管理 Controller
 */
@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {

    @DubboReference
    private ActivityService activityService;

    /**
     * 创建活动
     */
    @PostMapping
    public Result<String> create(@RequestBody ActivityRequest request) {
        return Result.success(activityService.create(request));
    }

    /**
     * 删除活动（仅待开始状态）
     */
    @DeleteMapping("/{activityNo}")
    public Result<Void> delete(@PathVariable String activityNo) {
        activityService.delete(activityNo, ActivityStatus.PENDING);
        return Result.success();
    }

    /**
     * 修改活动（仅待开始状态）
     */
    @PutMapping("/{activityNo}")
    public Result<Void> update(@PathVariable String activityNo, @RequestBody ActivityRequest request) {
        activityService.update(activityNo, ActivityStatus.PENDING, request);
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
     * 查询所有活动（各状态合并）
     */
    @GetMapping("/list")
    public Result<List<ActivityDTO>> listAll() {
        List<ActivityDTO> all = new ArrayList<>();
        for (ActivityStatus status : ActivityStatus.values()) {
            all.addAll(activityService.listByStatus(status));
        }
        return Result.success(all);
    }

    /**
     * 暂停活动（仅进行中状态）
     */
    @PutMapping("/{activityNo}/pause")
    public Result<Void> pause(@PathVariable String activityNo) {
        activityService.updateStatus(activityNo, ActivityStatus.PAUSED);
        return Result.success();
    }

    /**
     * 继续活动（仅暂停状态）
     */
    @PutMapping("/{activityNo}/resume")
    public Result<Void> resume(@PathVariable String activityNo) {
        activityService.updateStatus(activityNo, ActivityStatus.ACTIVE);
        return Result.success();
    }

    /**
     * 关闭活动（进行中或暂停状态）
     */
    @PutMapping("/{activityNo}/close")
    public Result<Void> close(@PathVariable String activityNo, @RequestParam ActivityStatus currentStatus) {
        activityService.updateStatus(activityNo, ActivityStatus.ENDED);
        return Result.success();
    }
}
