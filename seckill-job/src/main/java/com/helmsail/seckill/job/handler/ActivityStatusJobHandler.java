package com.helmsail.seckill.job.handler;

import com.helmsail.seckill.base.activity.*;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动状态流转任务
 *
 * 按时间推动活动状态机相邻两段：待开始到点激活（PENDING → ACTIVE）、进行中/已暂停到点关闭（ACTIVE、PAUSED → CLOSED）。
 * 同轮先激活后关闭：刚激活即到期的活动一轮内走完；单活动失败隔离，下轮自愈。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityStatusJobHandler {

    @DubboReference
    private ActivityService activityService;

    @XxlJob("activityStatusJob")
    public void execute() {
        log.info("活动状态流转任务启动");

        // 激活——待开始到点转进行中
        int activated = activatePending();

        // 关闭——进行中/已暂停到点转已关闭
        int closed = closeExpired();

        log.info("活动状态流转任务完成: 激活={}, 关闭={}", activated, closed);
    }

    /**
     * 激活——PENDING 开始时间已到 → ACTIVE
     */
    private int activatePending() {
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
        return activated;
    }

    /**
     * 关闭——ACTIVE/PAUSED 结束时间已过 → CLOSED
     *
     * 段内重新拉取，能兜住同轮刚激活即到期的活动；不查 PENDING（由激活段先行流转）。
     */
    private int closeExpired() {
        List<ActivityDTO> activities = new ArrayList<>();
        activities.addAll(activityService.listByStatus(ActivityStatus.ACTIVE));
        activities.addAll(activityService.listByStatus(ActivityStatus.PAUSED));

        LocalDateTime now = LocalDateTime.now();
        int closed = 0;

        for (ActivityDTO activity : activities) {
            LocalDateTime closeMoment = LocalDateTime.of(activity.getEndDate(), activity.getEndTime());
            if (now.isAfter(closeMoment)) {
                try {
                    activityService.close(activity.getActivityNo());
                    closed++;
                    log.info("活动已关闭: activityNo={}", activity.getActivityNo());
                } catch (Exception e) {
                    log.error("活动关闭失败: activityNo={}", activity.getActivityNo(), e);
                }
            }
        }
        return closed;
    }
}
