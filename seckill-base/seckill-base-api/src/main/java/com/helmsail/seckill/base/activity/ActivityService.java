package com.helmsail.seckill.base.activity;

import java.util.List;

/**
 * 活动 Dubbo 服务接口
 */
public interface ActivityService {

    /**
     * 创建活动
     */
    String create(ActivityRequest request);

    /**
     * 根据活动编号查询
     */
    ActivityDTO getByActivityNo(String activityNo);

    /**
     * 修改活动状态
     */
    void updateStatus(String activityNo, ActivityStatus targetStatus);

    /**
     * 根据活动状态查询列表
     */
    List<ActivityDTO> listByStatus(ActivityStatus status);

    /**
     * 修改活动信息
     */
    void update(String activityNo, ActivityRequest request);

    /**
     * 修改活动信息（需满足指定状态才能修改）
     */
    void update(String activityNo, ActivityStatus requiredStatus, ActivityRequest request);

    /**
     * 删除活动（需满足指定状态才能删除）
     */
    void delete(String activityNo, ActivityStatus requiredStatus);
}
