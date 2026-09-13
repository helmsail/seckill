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

        // 第一步：获取——全部待开始活动
        List<ActivityDTO> activities = fetchPendingActivities();
        LocalDateTime now = LocalDateTime.now();
        int warmedUp = 0;

        for (ActivityDTO activity : activities) {
            // 第二步：判断——仅处理进入预热窗口的活动
            if (!isInWarmUpWindow(activity, now)) {
                continue;
            }
            try {
                // 第三步：预热——单活动失败隔离，不中断其余活动
                warmUpActivity(activity);
                warmedUp++;
            } catch (Exception e) {
                log.error("活动预热失败: activityNo={}", activity.getActivityNo(), e);
            }
        }

        log.info("活动预热任务完成，共预热 {} 个活动", warmedUp);
    }

    /**
     * 第一步：获取——全部待开始活动
     *
     * DB 按状态过滤（非全表），是否在预热窗口留到内存中逐个判断。
     */
    private List<ActivityDTO> fetchPendingActivities() {
        return activityService.listByStatus(ActivityStatus.PENDING);
    }

    /**
     * 第二步：判断——是否进入预热窗口 [开始前 30 分钟, 开始时间]
     */
    private boolean isInWarmUpWindow(ActivityDTO activity, LocalDateTime now) {
        LocalDateTime activateMoment = LocalDateTime.of(activity.getStartDate(), activity.getStartTime());
        long secondsUntilStart = ChronoUnit.SECONDS.between(now, activateMoment);
        return secondsUntilStart >= 0 && secondsUntilStart <= WARM_UP_WINDOW_SECONDS;
    }

    /**
     * 第三步：预热——单个活动（拉取 SKU 列表后逐 key 写入）
     */
    private void warmUpActivity(ActivityDTO activity) throws Exception {
        String activityNo = activity.getActivityNo();
        log.info("开始预热活动: activityNo={}", activityNo);

        List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
        if (rows.isEmpty()) {
            // 无商品无需预热（同时避免为可被删除的空活动写入残留缓存）
            log.info("活动无商品，跳过预热: activityNo={}", activityNo);
            return;
        }

        warmUpActivityInfo(activity);
        warmUpProductList(activityNo, rows);
        warmUpStock(activityNo, rows);

        log.info("活动预热完成: activityNo={}, SKU数={}", activityNo, rows.size());
    }

    /**
     * 预热活动信息快照 → seckill:activity:info（Hash，field=activityNo）
     */
    private void warmUpActivityInfo(ActivityDTO activity) throws Exception {
        // 覆盖写：窗口内每分钟重复执行，重复覆盖只是刷新为最新快照
        redisService.hSet(SeckillRedisKey.KEY_ACTIVITY_INFO, activity.getActivityNo(),
                objectMapper.writeValueAsString(activity));
    }

    /**
     * 预热 SKU 列表快照 → seckill:activity:products:{activityNo}
     */
    private void warmUpProductList(String activityNo, List<SeckillProductSkuDTO> rows) throws Exception {
        redisService.set(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo),
                objectMapper.writeValueAsString(rows));
    }

    /**
     * 预热库存计数 → seckill:sku:stock:{activityNo}:{skuNo}
     *
     * 仅 key 不存在时初始化，绝不覆盖运行期已扣减的实时值。
     */
    private void warmUpStock(String activityNo, List<SeckillProductSkuDTO> rows) {
        for (SeckillProductSkuDTO row : rows) {
            String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, row.getSkuNo());
            redisService.setIfAbsent(stockKey, String.valueOf(row.getActivityStock()));
        }
    }
}
