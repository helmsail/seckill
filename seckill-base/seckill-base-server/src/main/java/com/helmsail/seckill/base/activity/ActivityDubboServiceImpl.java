package com.helmsail.seckill.base.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.helmsail.seckill.base.id.SeckillBusinessId;
import com.helmsail.seckill.base.productsku.SeckillProductSku;
import com.helmsail.seckill.base.productsku.SeckillProductSkuMapper;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动服务实现（Dubbo 暴露）
 *
 * 状态规则全部收敛在此：语义化操作 + 矩阵断言 + 条件更新（防并发）。
 * 写操作两类：create 非幂等（消费方须禁重试 retries=0）；删除/更新/状态流转
 * 有效果幂等或 CAS 保护（重复到达不坏数据），允许重试，消费方须按"报错≠未执行"处理。
 * （Dubbo 重试由 Consumer 决定，Provider 侧 retries 不生效）
 */
@Service
@DubboService
@RequiredArgsConstructor
public class ActivityDubboServiceImpl implements ActivityDubboService {

    /** 限购上限（sk_activity.purchase_limit 列为 TINYINT） */
    private static final int PURCHASE_LIMIT_MAX = 127;

    private final ActivityMapper activityMapper;
    private final SeckillProductSkuMapper seckillProductSkuMapper;

    // ========== CRUD ==========

    @Override
    public String create(ActivityRequest request) {
        validate(request);
        Activity activity = new Activity();
        activity.setActivityNo(SeckillBusinessId.SECKILL_ACTIVITY.buildNo());
        activity.setActivityStatus(ActivityStatus.PENDING.getCode());
        applyRequest(activity, request);
        activityMapper.insert(activity);
        return activity.getActivityNo();
    }

    @Override
    public void update(String activityNo, ActivityRequest request) {
        Activity activity = getExisting(activityNo);
        requireStatus(activity, ActivityStatus.PENDING, "仅待开始状态可修改");
        validate(request);
        applyRequest(activity, request);
        int rows = activityMapper.update(activity, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getActivityNo, activityNo)
                .eq(Activity::getActivityStatus, ActivityStatus.PENDING.getCode()));
        if (rows == 0) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "状态已变更，修改失败");
        }
    }

    @Override
    @Transactional
    public void delete(String activityNo) {
        // 锁定读活动行（与商品增删串行化）：消除"查 SKU 计数→删除"间的 TOCTOU 窗口
        Activity activity = getExistingForUpdate(activityNo);
        requireStatus(activity, ActivityStatus.PENDING, "仅待开始状态可删除");
        Long skuCount = seckillProductSkuMapper.selectCount(
                new LambdaQueryWrapper<SeckillProductSku>().eq(SeckillProductSku::getActivityNo, activityNo));
        if (skuCount != null && skuCount > 0) {
            throw new BizException(SeckillResultEnum.ACTIVITY_HAS_SKU);
        }
        int rows = activityMapper.delete(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getActivityNo, activityNo)
                .eq(Activity::getActivityStatus, ActivityStatus.PENDING.getCode()));
        if (rows == 0) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "状态已变更，删除失败");
        }
    }

    // ========== 查询 ==========

    @Override
    public ActivityDTO getByActivityNo(String activityNo) {
        return toDTO(getExisting(activityNo));
    }

    @Override
    public List<ActivityDTO> listByStatus(ActivityStatus status) {
        List<Activity> list = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityStatus, status.getCode())
                        .orderByDesc(Activity::getId));
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    public List<ActivityDTO> listAll() {
        // 主键倒序：雪花趋势递增 ≈ 创建时间倒序（最新在前），列表顺序稳定
        List<Activity> list = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().orderByDesc(Activity::getId));
        return list.stream().map(this::toDTO).toList();
    }

    // ========== 生命周期流转 ==========

    @Override
    public void activate(String activityNo) {
        Activity activity = getExisting(activityNo);
        requireStatus(activity, ActivityStatus.PENDING, "仅待开始状态可激活");
        if (LocalDateTime.now().isAfter(endMomentOf(activity))) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "活动已过结束时刻，无法激活");
        }
        transit(activity, ActivityStatus.ACTIVE);
    }

    @Override
    public void pause(String activityNo) {
        transit(getExisting(activityNo), ActivityStatus.PAUSED);
    }

    @Override
    public void resume(String activityNo) {
        Activity activity = getExisting(activityNo);
        if (LocalDateTime.now().isAfter(endMomentOf(activity))) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "活动已过结束时刻，无法继续");
        }
        transit(activity, ActivityStatus.ACTIVE);
    }

    @Override
    public void close(String activityNo) {
        transit(getExisting(activityNo), ActivityStatus.CLOSED);
    }

    // ========== 辅助方法 ==========

    /**
     * 参数校验（创建/修改通用）
     */
    private void validate(ActivityRequest request) {
        if (!StringUtils.hasText(request.getActivityName())) {
            throw new BizException(SeckillResultEnum.ACTIVITY_PARAM_ERROR.getCode(), "活动名称不能为空");
        }
        if (request.getStartDate() == null || request.getEndDate() == null
                || request.getStartTime() == null || request.getEndTime() == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_PARAM_ERROR.getCode(), "活动日期与时段不能为空");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BizException(SeckillResultEnum.ACTIVITY_PARAM_ERROR.getCode(), "开始日期不能晚于结束日期");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BizException(SeckillResultEnum.ACTIVITY_PARAM_ERROR.getCode(), "当天开始时间必须早于结束时间（不支持跨天）");
        }
        if (!WeekBitmap.isValid(request.getWeekBitmap())) {
            throw new BizException(SeckillResultEnum.ACTIVITY_PARAM_ERROR.getCode(), "周位图必填且必须在 1~127 之间");
        }
        if (request.getPurchaseLimit() != null
                && (request.getPurchaseLimit() < 0 || request.getPurchaseLimit() > PURCHASE_LIMIT_MAX)) {
            throw new BizException(SeckillResultEnum.ACTIVITY_PARAM_ERROR.getCode(),
                    "限购数量必须在 0~" + PURCHASE_LIMIT_MAX + " 之间");
        }
    }

    /**
     * 将请求字段应用到活动实体（purchaseLimit 空值归一为 0）
     */
    private void applyRequest(Activity activity, ActivityRequest request) {
        activity.setActivityName(request.getActivityName());
        activity.setStartDate(request.getStartDate());
        activity.setEndDate(request.getEndDate());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setWeekBitmap(request.getWeekBitmap());
        activity.setPurchaseLimit(request.getPurchaseLimit() == null ? 0 : request.getPurchaseLimit());
    }

    /**
     * 断言活动处于指定状态（失败消息追加当前状态）
     */
    private void requireStatus(Activity activity, ActivityStatus required, String message) {
        ActivityStatus current = ActivityStatus.byCode(activity.getActivityStatus());
        if (current != required) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(),
                    message + "，当前状态: " + current.getDesc());
        }
    }

    /**
     * 查询活动（不存在抛异常）
     */
    private Activity getExisting(String activityNo) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo));
        if (activity == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    /**
     * 锁定读活动（存在性校验同 getExisting）；调用方须已在事务内
     */
    private Activity getExistingForUpdate(String activityNo) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo).last("FOR UPDATE"));
        if (activity == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    /**
     * 活动结束时刻（结束日期 + 当天结束时间）
     */
    private LocalDateTime endMomentOf(Activity activity) {
        return LocalDateTime.of(activity.getEndDate(), activity.getEndTime());
    }

    /**
     * 状态流转：矩阵断言 + 条件更新（并发下状态已变则失败）
     */
    private void transit(Activity activity, ActivityStatus targetStatus) {
        ActivityStatus current = ActivityStatus.byCode(activity.getActivityStatus());
        if (!ActivityStatus.canTransit(current, targetStatus)) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(),
                    "状态流转不合法: " + current.getDesc() + " → " + targetStatus.getDesc());
        }
        int rows = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getActivityNo, activity.getActivityNo())
                .eq(Activity::getActivityStatus, current.getCode())
                .set(Activity::getActivityStatus, targetStatus.getCode()));
        if (rows == 0) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "状态已变更，请刷新后重试");
        }
    }

    private ActivityDTO toDTO(Activity activity) {
        ActivityDTO dto = new ActivityDTO();
        dto.setId(activity.getId());
        dto.setActivityNo(activity.getActivityNo());
        dto.setActivityName(activity.getActivityName());
        dto.setStartDate(activity.getStartDate());
        dto.setEndDate(activity.getEndDate());
        dto.setStartTime(activity.getStartTime());
        dto.setEndTime(activity.getEndTime());
        dto.setWeekBitmap(activity.getWeekBitmap());
        dto.setPurchaseLimit(activity.getPurchaseLimit());
        dto.setActivityStatus(ActivityStatus.byCode(activity.getActivityStatus()));
        return dto;
    }
}
