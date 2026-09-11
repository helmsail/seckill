package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.redis.SeckillCacheKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 活动刷新任务
 *
 * 刷新进行中活动的展示缓存（活动信息 + 商品SKU列表）。
 * 不触碰库存计数 key（运行期实时值不可被刷新覆盖）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityRefreshJobHandler {

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @XxlJob("activityRefreshJob")
    public void execute() {
        log.info("活动刷新任务启动");

        List<ActivityDTO> activities = activityService.listByStatus(ActivityStatus.ACTIVE);
        int refreshed = 0;

        for (ActivityDTO activity : activities) {
            try {
                refreshActivity(activity);
                refreshed++;
            } catch (Exception e) {
                log.error("活动刷新失败: activityNo={}", activity.getActivityNo(), e);
            }
        }

        log.info("活动刷新任务完成，共刷新 {} 个活动", refreshed);
    }

    private void refreshActivity(ActivityDTO activity) throws Exception {
        String activityNo = activity.getActivityNo();
        log.info("开始刷新活动: activityNo={}", activityNo);

        List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);

        redisService.hSet(SeckillCacheKey.KEY_ACTIVITY_INFO, activityNo,
                objectMapper.writeValueAsString(activity));
        redisService.set(String.format(SeckillCacheKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo),
                objectMapper.writeValueAsString(rows));

        log.info("活动刷新完成: activityNo={}, SKU数={}", activityNo, rows.size());
    }
}
