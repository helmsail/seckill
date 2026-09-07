package com.helmsail.seckill.base.activity;

import java.util.List;

/**
 * 活动内部服务接口
 */
public interface ActivityBizService {

    String create(ActivityRequest request);

    ActivityDTO getByActivityNo(String activityNo);

    void updateStatus(String activityNo, ActivityStatus targetStatus);

    List<ActivityDTO> listByStatus(ActivityStatus status);

    void update(String activityNo, ActivityRequest request);

    void update(String activityNo, ActivityStatus requiredStatus, ActivityRequest request);

    void delete(String activityNo, ActivityStatus requiredStatus);
}
