package com.helmsail.seckill.base.activity;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 活动 Dubbo 服务实现
 */
@DubboService
@RequiredArgsConstructor
public class ActivityDubboServiceImpl implements ActivityService {

    private final ActivityBizService activityBizService;

    @Override
    public String create(ActivityRequest request) {
        return activityBizService.create(request);
    }

    @Override
    public ActivityDTO getByActivityNo(String activityNo) {
        return activityBizService.getByActivityNo(activityNo);
    }

    @Override
    public void updateStatus(String activityNo, ActivityStatus targetStatus) {
        activityBizService.updateStatus(activityNo, targetStatus);
    }

    @Override
    public List<ActivityDTO> listByStatus(ActivityStatus status) {
        return activityBizService.listByStatus(status);
    }

    @Override
    public void update(String activityNo, ActivityRequest request) {
        activityBizService.update(activityNo, request);
    }

    @Override
    public void update(String activityNo, ActivityStatus requiredStatus, ActivityRequest request) {
        activityBizService.update(activityNo, requiredStatus, request);
    }

    @Override
    public void delete(String activityNo, ActivityStatus requiredStatus) {
        activityBizService.delete(activityNo, requiredStatus);
    }
}
