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
    public void activate(String activityNo) {
        activityBizService.activate(activityNo);
    }

    @Override
    public void pause(String activityNo) {
        activityBizService.pause(activityNo);
    }

    @Override
    public void resume(String activityNo) {
        activityBizService.resume(activityNo);
    }

    @Override
    public void close(String activityNo) {
        activityBizService.close(activityNo);
    }

    @Override
    public void update(String activityNo, ActivityRequest request) {
        activityBizService.update(activityNo, request);
    }

    @Override
    public void delete(String activityNo) {
        activityBizService.delete(activityNo);
    }

    @Override
    public ActivityDTO getByActivityNo(String activityNo) {
        return activityBizService.getByActivityNo(activityNo);
    }

    @Override
    public List<ActivityDTO> listByStatus(ActivityStatus status) {
        return activityBizService.listByStatus(status);
    }

    @Override
    public List<ActivityDTO> listAll() {
        return activityBizService.listAll();
    }
}
