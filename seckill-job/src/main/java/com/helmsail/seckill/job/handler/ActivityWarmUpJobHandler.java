package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 活动预热任务
 *
 * 待开始活动进入激活窗口前 30 分钟，将活动信息、商品SKU列表与库存计数写入 Redis。
 * 库存计数仅在 key 不存在时初始化（绝不覆盖运行期已扣减的实时值）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityWarmUpJobHandler {

    private static final int WARM_UP_WINDOW_SECONDS = 30 * 60;

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @XxlJob("activityWarmUpJob")
    public void execute() {
        log.info("活动预热任务启动");

        List<ActivityDTO> activities = activityService.listByStatus(ActivityStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        int warmedUp = 0;

        for (ActivityDTO activity : activities) {
            if (!isInWarmUpWindow(activity, now)) {
                continue;
            }
            try {
                warmUpActivity(activity);
                warmedUp++;
            } catch (Exception e) {
                log.error("活动预热失败: activityNo={}", activity.getActivityNo(), e);
            }
        }

        log.info("活动预热任务完成，共预热 {} 个活动", warmedUp);
    }

    private boolean isInWarmUpWindow(ActivityDTO activity, LocalDateTime now) {
        LocalDateTime activateMoment = LocalDateTime.of(activity.getStartDate(), activity.getStartTime());
        long secondsUntilStart = ChronoUnit.SECONDS.between(now, activateMoment);
        return secondsUntilStart >= 0 && secondsUntilStart <= WARM_UP_WINDOW_SECONDS;
    }

    private void warmUpActivity(ActivityDTO activity) throws Exception {
        String activityNo = activity.getActivityNo();
        log.info("开始预热活动: activityNo={}", activityNo);

        List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
        if (rows.isEmpty()) {
            // 无商品无需预热（同时避免为可被删除的空活动写入残留缓存）
            log.info("活动无商品，跳过预热: activityNo={}", activityNo);
            return;
        }

        redisService.hSet(SeckillRedisKey.KEY_ACTIVITY_INFO, activityNo,
                objectMapper.writeValueAsString(activity));
        redisService.set(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo),
                objectMapper.writeValueAsString(rows));
        initStock(activityNo, rows);

        log.info("活动预热完成: activityNo={}, SKU数={}", activityNo, rows.size());
    }

    private void initStock(String activityNo, List<SeckillProductSkuDTO> rows) {
        for (SeckillProductSkuDTO row : rows) {
            String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, row.getSkuNo());
            redisService.setIfAbsent(stockKey, String.valueOf(row.getActivityStock()));
            // 初始总量键：restore（关单/回滚回补）的上界参照，写入后不再变更
            String totalKey = String.format(SeckillRedisKey.KEY_SKU_STOCK_TOTAL, activityNo, row.getSkuNo());
            redisService.setIfAbsent(totalKey, String.valueOf(row.getActivityStock()));
        }
    }
}
