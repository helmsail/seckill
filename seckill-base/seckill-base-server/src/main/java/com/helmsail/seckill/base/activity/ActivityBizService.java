package com.helmsail.seckill.base.activity;

import java.util.List;

/**
 * 活动内部服务接口
 */
public interface ActivityBizService {

    String create(ActivityRequest request);

    void activate(String activityNo);

    void pause(String activityNo);

    void resume(String activityNo);

    void close(String activityNo);

    void update(String activityNo, ActivityRequest request);

    void delete(String activityNo);

    ActivityDTO getByActivityNo(String activityNo);

    List<ActivityDTO> listByStatus(ActivityStatus status);

    List<ActivityDTO> listAll();
}
