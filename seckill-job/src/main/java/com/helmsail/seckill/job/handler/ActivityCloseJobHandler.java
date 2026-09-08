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

        List<ActivityDTO> activeActivities = activityService.listByStatus(ActivityStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        int closed = 0;

        for (ActivityDTO activity : activeActivities) {
            if (now.isAfter(activity.getEndTime())) {
                try {
                    activityService.updateStatus(activity.getActivityNo(), ActivityStatus.ENDED);
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
