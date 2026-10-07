package com.helmsail.seckill.job.handler;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityDubboService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动信息缓存清理任务（活动终态清理：Hash field 无法按 field 过期，须手动 HDEL）
 *
 * 扫已关闭活动：结束时刻已过安全期（覆盖关闭瞬间在途请求与关单链路）→ HDEL 活动信息 field。
 * field 删除无正确性影响：C 端 miss 回源 DB（关闭态照常返回并写负标记）；
 * 限购读取只发生于进行中活动（DB 终判拦截已关闭活动的新请求）。
 * 幂等：重复 HDEL 不存在的 field 返回 0，无害；每轮全量扫描（随历史累积可改时间窗查询优化）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityInfoCleanupJobHandler {

    /** 安全期（小时）：结束时刻后须满该时长才清理（与 StockArchiveJobHandler 同源） */
    private static final int SAFETY_WINDOW_HOURS = 24;

    @DubboReference
    private ActivityDubboService activityService;

    private final RedisService redisService;

    @XxlJob("activityInfoCleanupJob")
    public void execute() {
        List<ActivityDTO> closed = activityService.listByStatus(ActivityStatus.CLOSED);
        LocalDateTime safeBefore = LocalDateTime.now().minusHours(SAFETY_WINDOW_HOURS);

        int cleaned = 0;
        for (ActivityDTO activity : closed) {
            LocalDateTime closeMoment = LocalDateTime.of(activity.getEndDate(), activity.getEndTime());
            if (closeMoment.isAfter(safeBefore)) {
                continue;
            }
            try {
                Long removed = redisService.hDel(SeckillRedisKey.KEY_ACTIVITY_INFO, activity.getActivityNo());
                if (removed != null && removed > 0) {
                    cleaned++;
                }
            } catch (Exception e) {
                log.error("活动信息缓存清理失败: activityNo={}", activity.getActivityNo(), e);
            }
        }
        log.info("活动信息缓存清理任务完成: 关闭活动={}, 本轮清理={}", closed.size(), cleaned);
    }
}
