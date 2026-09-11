package com.helmsail.seckill.job.handler;

import com.helmsail.seckill.base.activity.*;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动状态变更任务
 *
 * 将待开始的活动状态修改为进行中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusJobHandler {

    @DubboReference
    private ActivityService activityService;

    @XxlJob("activityStatusJob")
    public void execute() {
        log.info("活动状态变更任务启动");

        List<ActivityDTO> pendingActivities = activityService.listByStatus(ActivityStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        int activated = 0;

        for (ActivityDTO activity : pendingActivities) {
            LocalDateTime activateMoment = LocalDateTime.of(activity.getStartDate(), activity.getStartTime());
            if (!now.isBefore(activateMoment)) {
                try {
                    activityService.activate(activity.getActivityNo());
                    activated++;
                    log.info("活动已激活: activityNo={}", activity.getActivityNo());
                } catch (Exception e) {
                    log.error("活动激活失败: activityNo={}", activity.getActivityNo(), e);
                }
            }
        }

        log.info("活动状态变更任务完成，共激活 {} 个活动", activated);
    }
}
