package com.helmsail.seckill.base.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.helmsail.seckill.base.id.SeckillBusinessPrefix;
import com.helmsail.seckill.base.productsku.SeckillProductSku;
import com.helmsail.seckill.base.productsku.SeckillProductSkuMapper;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动服务实现（Dubbo 暴露）
 *
 * 状态规则全部收敛在此：语义化操作 + 矩阵断言 + 条件更新（防并发）。
 * retries = 0：本服务含非幂等写操作（创建/删除/状态流转），自动重试会产生重复副作用
 */
@Service
@DubboService(retries = 0)
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    /** 限购上限（sk_activity.purchase_limit 列为 TINYINT） */
    private static final int PURCHASE_LIMIT_MAX = 127;

    private final ActivityMapper activityMapper;
    private final SeckillProductSkuMapper seckillProductSkuMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public String create(ActivityRequest request) {
        validate(request);
        Activity activity = new Activity();
        activity.setActivityNo(SeckillBusinessPrefix.SECKILL_ACTIVITY.buildNo(snowflakeIdGenerator.nextId()));
        activity.setActivityName(request.getActivityName());
        activity.setStartDate(request.getStartDate());
        activity.setEndDate(request.getEndDate());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setWeekBitmap(request.getWeekBitmap() == null ? WeekBitmap.ALL : request.getWeekBitmap());
        activity.setPurchaseLimit(request.getPurchaseLimit() == null ? 0 : request.getPurchaseLimit());
        activity.setActivityStatus(ActivityStatus.PENDING.getCode());
        activity.setRemark(request.getRemark());
        activityMapper.insert(activity);
        return activity.getActivityNo();
    }

    @Override
    public void activate(String activityNo) {
        Activity activity = getExisting(activityNo);
        ActivityStatus current = ActivityStatus.byCode(activity.getActivityStatus());
        if (current != ActivityStatus.PENDING) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(),
                    "仅待开始状态可激活，当前状态: " + current.getDesc());
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
        LocalDateTime endMoment = LocalDateTime.of(activity.getEndDate(), activity.getEndTime());
        if (LocalDateTime.now().isAfter(endMoment)) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "活动已过结束时刻，无法继续");
        }
        transit(activity, ActivityStatus.ACTIVE);
    }

    @Override
    public void close(String activityNo) {
        transit(getExisting(activityNo), ActivityStatus.CLOSED);
    }

    @Override
    public void update(String activityNo, ActivityRequest request) {
        Activity activity = getExisting(activityNo);
        ActivityStatus current = ActivityStatus.byCode(activity.getActivityStatus());
        if (current != ActivityStatus.PENDING) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(),
                    "仅待开始状态可修改，当前状态: " + current.getDesc());
        }
        validate(request);
        activity.setActivityName(request.getActivityName());
        activity.setStartDate(request.getStartDate());
        activity.setEndDate(request.getEndDate());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setWeekBitmap(request.getWeekBitmap() == null ? WeekBitmap.ALL : request.getWeekBitmap());
        activity.setPurchaseLimit(request.getPurchaseLimit() == null ? 0 : request.getPurchaseLimit());
        activity.setRemark(request.getRemark());
        int rows = activityMapper.update(activity, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getActivityNo, activityNo)
                .eq(Activity::getActivityStatus, ActivityStatus.PENDING.getCode()));
        if (rows == 0) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "状态已变更，修改失败");
        }
    }

    @Override
    public void delete(String activityNo) {
        Activity activity = getExisting(activityNo);
        ActivityStatus current = ActivityStatus.byCode(activity.getActivityStatus());
        if (current != ActivityStatus.PENDING) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(),
                    "仅待开始状态可删除，当前状态: " + current.getDesc());
        }
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

    @Override
    public ActivityDTO getByActivityNo(String activityNo) {
        return toDTO(getExisting(activityNo));
    }

    @Override
    public List<ActivityDTO> listByStatus(ActivityStatus status) {
        List<Activity> list = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityStatus, status.getCode()));
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    public List<ActivityDTO> listAll() {
        List<Activity> list = activityMapper.selectList(new LambdaQueryWrapper<>());
        return list.stream().map(this::toDTO).toList();
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

    /**
     * 参数校验（创建/修改通用）
     */
    private void validate(ActivityRequest request) {
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
        if (request.getWeekBitmap() != null && !WeekBitmap.isValid(request.getWeekBitmap())) {
            throw new BizException(SeckillResultEnum.ACTIVITY_PARAM_ERROR.getCode(), "周位图必须在 1~127 之间");
        }
        if (request.getPurchaseLimit() != null
                && (request.getPurchaseLimit() < 0 || request.getPurchaseLimit() > PURCHASE_LIMIT_MAX)) {
            throw new BizException(SeckillResultEnum.ACTIVITY_PARAM_ERROR.getCode(),
                    "限购数量必须在 0~" + PURCHASE_LIMIT_MAX + " 之间");
        }
    }

    private Activity getExisting(String activityNo) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo));
        if (activity == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_FOUND);
        }
        return activity;
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
        dto.setRemark(activity.getRemark());
        return dto;
    }
}
