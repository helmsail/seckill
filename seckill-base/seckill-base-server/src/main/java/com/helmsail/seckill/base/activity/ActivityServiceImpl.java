package com.helmsail.seckill.base.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helmsail.seckill.base.id.SeckillBusinessPrefix;
import com.helmsail.seckill.common.id.SnowflakeIdGenerator;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 活动服务实现
 */
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityBizService {

    private final ActivityMapper activityMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public String create(ActivityRequest request) {
        Activity activity = new Activity();
        activity.setActivityNo(String.valueOf(SeckillBusinessPrefix.SECKILL_ACTIVITY.getPrefix()) + snowflakeIdGenerator.nextId());
        activity.setActivityName(request.getActivityName());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setEffectiveType(request.getEffectiveType().getCode());
        activity.setEffectiveDays(request.getEffectiveDays());
        activity.setEffectiveStart(request.getEffectiveStart());
        activity.setEffectiveEnd(request.getEffectiveEnd());
        activity.setPurchaseLimit(request.getPurchaseLimit());
        activity.setActivityStatus(ActivityStatus.PENDING.getCode());
        activity.setRemark(request.getRemark());
        activityMapper.insert(activity);
        return activity.getActivityNo();
    }

    @Override
    public ActivityDTO getByActivityNo(String activityNo) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo));
        if (activity == null) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
        return toDTO(activity);
    }

    @Override
    public void updateStatus(String activityNo, ActivityStatus targetStatus) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo));
        if (activity == null) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
        ActivityStatus currentStatus = ActivityStatus.values()[activity.getActivityStatus()];
        if (!ActivityStatus.canTransit(currentStatus, targetStatus)) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(),
                    "状态流转不合法: " + currentStatus.getDesc() + " → " + targetStatus.getDesc());
        }
        activity.setActivityStatus(targetStatus.getCode());
        activityMapper.updateById(activity);
    }

    @Override
    public List<ActivityDTO> listByStatus(ActivityStatus status) {
        List<Activity> list = activityMapper.selectList(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityStatus, status.getCode()));
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    public void update(String activityNo, ActivityStatus requiredStatus, ActivityRequest request) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo));
        if (activity == null) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
        ActivityStatus currentStatus = ActivityStatus.values()[activity.getActivityStatus()];
        if (currentStatus != requiredStatus) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(),
                    "当前状态不满足修改条件，当前状态: " + currentStatus.getDesc());
        }
        updateFields(activity, request);
        activityMapper.updateById(activity);
    }

    @Override
    public void delete(String activityNo, ActivityStatus requiredStatus) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo));
        if (activity == null) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
        ActivityStatus currentStatus = ActivityStatus.values()[activity.getActivityStatus()];
        if (currentStatus != requiredStatus) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(),
                    "当前状态不满足删除条件，当前状态: " + currentStatus.getDesc());
        }
        activityMapper.deleteById(activity.getId());
    }

    private void updateFields(Activity activity, ActivityRequest request) {
        if (request.getActivityName() != null) activity.setActivityName(request.getActivityName());
        if (request.getStartTime() != null) activity.setStartTime(request.getStartTime());
        if (request.getEndTime() != null) activity.setEndTime(request.getEndTime());
        if (request.getEffectiveType() != null) activity.setEffectiveType(request.getEffectiveType().getCode());
        if (request.getEffectiveDays() != null) activity.setEffectiveDays(request.getEffectiveDays());
        if (request.getEffectiveStart() != null) activity.setEffectiveStart(request.getEffectiveStart());
        if (request.getEffectiveEnd() != null) activity.setEffectiveEnd(request.getEffectiveEnd());
        if (request.getPurchaseLimit() != null) activity.setPurchaseLimit(request.getPurchaseLimit());
        if (request.getRemark() != null) activity.setRemark(request.getRemark());
    }

    private ActivityDTO toDTO(Activity activity) {
        ActivityDTO dto = new ActivityDTO();
        dto.setId(activity.getId());
        dto.setActivityNo(activity.getActivityNo());
        dto.setActivityName(activity.getActivityName());
        dto.setStartTime(activity.getStartTime());
        dto.setEndTime(activity.getEndTime());
        dto.setEffectiveType(EffectiveType.values()[activity.getEffectiveType()]);
        dto.setEffectiveDays(activity.getEffectiveDays());
        dto.setEffectiveStart(activity.getEffectiveStart());
        dto.setEffectiveEnd(activity.getEffectiveEnd());
        dto.setPurchaseLimit(activity.getPurchaseLimit());
        dto.setActivityStatus(ActivityStatus.values()[activity.getActivityStatus()]);
        dto.setRemark(activity.getRemark());
        return dto;
    }
}
