package com.helmsail.seckill.job.handler;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCloseJobHandler {

    @DubboReference
    private ActivityService activityService;

    @XxlJob("activityCloseJob")
    public void execute() {
        log.info("活动关闭任务启动");

        // 查询 ACTIVE 和 PAUSED 状态的活动（PENDING 由激活任务先行流转，无需在此处理）
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

        log.info("活动关闭任务完成，共关闭 {} 个活动", closed);
    }
}
