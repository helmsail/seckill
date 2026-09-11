package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.support.api.sku.SkuService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动状态同步与刷新任务
 *
 * 1. 状态同步：将 PENDING/ACTIVE/PAUSED 活动的 DB 权威状态覆盖写入 Redis Hash，
 *    使暂停/关闭/激活等状态变更（含管理端手动操作）在运行期生效；
 * 2. 终态清理：CLOSED 活动先将未售库存归还主域，再移除 Hash 条目、在售名单与商品列表快照；
 * 3. 展示刷新：刷新进行中活动的商品SKU列表快照。
 *
 * 不触碰进行中活动的库存计数 key（运行期实时值不可被刷新覆盖）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityRefreshJobHandler {

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    @DubboReference
    private SkuService skuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /** 读取即清零：返回剩余值并删除键（-1 表示键不存在） */
    private static final String TAKE_STOCK_LUA =
            "local v = redis.call('get', KEYS[1]) "
            + "if v == nil then return -1 end "
            + "redis.call('del', KEYS[1]) "
            + "return tonumber(v)";

    @XxlJob("activityRefreshJob")
    public void execute() {
        log.info("活动状态同步与刷新任务启动");

        int synced = syncStatus(ActivityStatus.PENDING)
                + syncStatus(ActivityStatus.ACTIVE)
                + syncStatus(ActivityStatus.PAUSED);

        int cleaned = cleanClosed();

        int orphans = cleanOrphans();

        int refreshed = refreshActiveProducts();

        log.info("活动状态同步与刷新任务完成: 同步状态={}, 清理终态={}, 清理孤儿={}, 刷新商品={}",
                synced, cleaned, orphans, refreshed);
    }

    /**
     * 同步指定状态的活动到 Redis Hash（覆盖写入）
     */
    private int syncStatus(ActivityStatus status) {
        List<ActivityDTO> activities = activityService.listByStatus(status);
        int synced = 0;
        for (ActivityDTO activity : activities) {
            try {
                redisService.hSet(SeckillRedisKey.KEY_ACTIVITY_INFO, activity.getActivityNo(),
                        objectMapper.writeValueAsString(activity));
                synced++;
            } catch (Exception e) {
                log.error("活动状态同步失败: activityNo={}, status={}", activity.getActivityNo(), status, e);
            }
        }
        return synced;
    }

    /**
     * 清理已关闭活动：先归还未售库存，再清理运行期数据（重复执行为幂等操作）
     */
    private int cleanClosed() {
        List<ActivityDTO> activities = activityService.listByStatus(ActivityStatus.CLOSED);
        int cleaned = 0;
        for (ActivityDTO activity : activities) {
            try {
                String activityNo = activity.getActivityNo();
                restoreStock(activityNo);
                redisService.hDel(SeckillRedisKey.KEY_ACTIVITY_INFO, activityNo);
                redisService.delete(String.format(SeckillRedisKey.KEY_ACTIVITY_SHELF, activityNo));
                redisService.delete(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo));
                cleaned++;
            } catch (Exception e) {
                log.error("终态活动清理失败: activityNo={}", activity.getActivityNo(), e);
            }
        }
        return cleaned;
    }

    /**
     * 归还未售库存到主域（剩余值以 Redis 运行期计数为准，读即清零保证同一值只归还一次）
     *
     * 幂等收敛：每轮对终态活动执行——活动关闭后在途订单关单回补产生的残留值，
     * 会在后续轮次被读到继续归还，最终收敛。归还失败的 SKU 写回原值，下轮重试。
     * 注：未预热（库存键不存在）的 SKU 视为从未进入运行期，跳过归还。
     * TODO 终态活动积累较多后，可增加"最近关闭"过滤减少每轮遍历。
     */
    private void restoreStock(String activityNo) {
        List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
        for (SeckillProductSkuDTO row : rows) {
            String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, row.getSkuNo());
            String totalKey = String.format(SeckillRedisKey.KEY_SKU_STOCK_TOTAL, activityNo, row.getSkuNo());
            Long remain = redisService.executeLua(TAKE_STOCK_LUA, Collections.singletonList(stockKey));
            if (remain == null) {
                continue;
            }
            if (remain > 0) {
                try {
                    skuService.addStock(row.getSkuNo(), remain.intValue());
                } catch (Exception e) {
                    redisService.set(stockKey, String.valueOf(remain));
                    log.error("库存归还主域失败: activityNo={}, skuNo={}, remain={}",
                            activityNo, row.getSkuNo(), remain, e);
                    continue;
                }
            }
            redisService.delete(totalKey);
        }
    }

    /**
     * 清理孤儿活动缓存（Redis 有记录但 DB 不存在：如待开始活动被删除后的残留）
     *
     * 声明式收敛：每轮对比 DB 全量活动号与 Hash 字段，差集即孤儿。
     */
    private int cleanOrphans() {
        Set<String> existingNos = activityService.listAll().stream()
                .map(ActivityDTO::getActivityNo)
                .collect(Collectors.toSet());
        Map<Object, Object> cached = redisService.hGetAll(SeckillRedisKey.KEY_ACTIVITY_INFO);
        int cleaned = 0;
        for (Object field : cached.keySet()) {
            String activityNo = (String) field;
            if (existingNos.contains(activityNo)) {
                continue;
            }
            try {
                redisService.hDel(SeckillRedisKey.KEY_ACTIVITY_INFO, activityNo);
                redisService.delete(String.format(SeckillRedisKey.KEY_ACTIVITY_SHELF, activityNo));
                redisService.delete(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo));
                cleaned++;
                log.warn("孤儿活动缓存已清理: activityNo={}", activityNo);
            } catch (Exception e) {
                log.error("孤儿活动缓存清理失败: activityNo={}", activityNo, e);
            }
        }
        return cleaned;
    }

    /**
     * 刷新进行中活动的商品SKU列表快照
     */
    private int refreshActiveProducts() {
        List<ActivityDTO> activities = activityService.listByStatus(ActivityStatus.ACTIVE);
        int refreshed = 0;
        for (ActivityDTO activity : activities) {
            try {
                String activityNo = activity.getActivityNo();
                List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
                redisService.set(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo),
                        objectMapper.writeValueAsString(rows));
                refreshed++;
            } catch (Exception e) {
                log.error("活动商品刷新失败: activityNo={}", activity.getActivityNo(), e);
            }
        }
        return refreshed;
    }
}
