package com.helmsail.seckill.job.handler;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 活动在售名单同步任务（轻量、高频）
 *
 * 全量重建进行中活动的 Redis 在售名单（SET，成员=上架 skuNo）。
 * 声明式全量覆盖：单次失败仅延迟一个周期，下次成功自动修复。
 * 正确性不依赖本任务（processor 以 DB 权威状态终判），本任务只服务前置过滤与展示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityShelfJobHandler {

    private static final String REBUILD_LUA =
            "redis.call('del', KEYS[1]) " +
            "for i=1,#ARGV do redis.call('sadd', KEYS[1], ARGV[i]) end " +
            "return 1";

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    private final RedisService redisService;

    @XxlJob("activityShelfJob")
    public void execute() {
        log.info("活动在售名单同步任务启动");

        List<ActivityDTO> activities = activityService.listByStatus(ActivityStatus.ACTIVE);
        int synced = 0;

        for (ActivityDTO activity : activities) {
            try {
                rebuildShelf(activity.getActivityNo());
                synced++;
            } catch (Exception e) {
                log.error("在售名单同步失败: activityNo={}", activity.getActivityNo(), e);
            }
        }

        log.info("活动在售名单同步任务完成，共同步 {} 个活动", synced);
    }

    private void rebuildShelf(String activityNo) {
        List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
        List<String> onShelfSkuNos = rows.stream()
                .filter(row -> row.getShelfStatus() != null && row.getShelfStatus() == 1)
                .map(SeckillProductSkuDTO::getSkuNo)
                .toList();
        String key = String.format(SeckillRedisKey.KEY_ACTIVITY_SHELF, activityNo);
        redisService.executeLua(REBUILD_LUA, Collections.singletonList(key), onShelfSkuNos.toArray());
    }
}
