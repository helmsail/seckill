package com.helmsail.seckill.base.activity;

import com.helmsail.seckill.common.exception.BizException;

import java.util.List;

/**
 * 活动 Dubbo 服务接口
 *
 * 状态操作均为语义化方法，状态规则全部收敛在服务端
 */
public interface ActivityService {

    /**
     * 创建活动
     */
    String create(ActivityRequest request) throws BizException;

    /**
     * 激活活动（待开始 → 进行中，供自动任务调用）
     */
    void activate(String activityNo) throws BizException;

    /**
     * 暂停活动（进行中 → 已暂停）
     */
    void pause(String activityNo) throws BizException;

    /**
     * 继续活动（已暂停 → 进行中，需活动尚未整体结束）
     */
    void resume(String activityNo) throws BizException;

    /**
     * 关闭活动（进行中/已暂停 → 已关闭）
     */
    void close(String activityNo) throws BizException;

    /**
     * 修改活动（仅待开始状态可修改）
     */
    void update(String activityNo, ActivityRequest request) throws BizException;

    /**
     * 删除活动（仅待开始状态可删除）
     */
    void delete(String activityNo) throws BizException;

    /**
     * 根据活动编号查询
     */
    ActivityDTO getByActivityNo(String activityNo) throws BizException;

    /**
     * 根据活动状态查询列表
     */
    List<ActivityDTO> listByStatus(ActivityStatus status) throws BizException;

    /**
     * 查询所有活动
     */
    List<ActivityDTO> listAll() throws BizException;
}
