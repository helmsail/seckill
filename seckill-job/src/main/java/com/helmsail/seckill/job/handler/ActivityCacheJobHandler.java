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
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动缓存同步任务
 *
 * 将"窗口内待开始 / 进行中 / 已暂停"活动从 base 库搬进 Redis：获取 → 过滤合并 → 逐个处理。
 *
 * 1. 获取：PENDING/ACTIVE/PAUSED 各拉一次；
 * 2. 过滤合并：待开始只留进入预热窗口的（窗口外不缓存——远期活动提前缓存没有意义），加进行中、已暂停合成处理列表；
 * 3. 逐个处理（四个部分）：
 *    - 活动信息快照：覆盖写 seckill:activity:info（Hash，field=activityNo），使暂停/激活/信息变更（含管理端手动操作）在运行期生效；
 *    - 活动与SKU：覆盖写商品快照，库存与上下架从快照剔除、抠出后由下方两方法单独写 key；
 *    - 上下架：单独方法覆盖写在售名单（下架同样写 0，不能跳过），激活瞬间名单已就绪；
 *    - 库存：单独方法缺省初始化，需判断活动状态——仅待开始写入（setIfAbsent 只补缺不覆盖）。
 *
 * 库存仅在本任务"待开始"分支被写入，其余任何分支与状态永不触碰库存键——
 * 运行期实时值只被扣减/回补/终态归还修改；所有 key 不设 TTL，终态回收与孤儿清理见 ActivityRecoveryJobHandler。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCacheJobHandler {

    private static final int WARM_UP_WINDOW_SECONDS = 30 * 60;

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @XxlJob("activityCacheJob")
    public void execute() {
        log.info("活动缓存同步任务启动");

        // 获取——三种状态各拉一次
        List<ActivityDTO> pending = activityService.listByStatus(ActivityStatus.PENDING);
        List<ActivityDTO> active = activityService.listByStatus(ActivityStatus.ACTIVE);
        List<ActivityDTO> paused = activityService.listByStatus(ActivityStatus.PAUSED);

        // 过滤合并——待开始只留进入预热窗口的（窗口外不缓存），加进行中、已暂停；这个列表即全部要处理的活动
        List<ActivityDTO> all = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (ActivityDTO activity : pending) {
            if (isInWarmUpWindow(activity, now)) {
                all.add(activity);
            }
        }
        all.addAll(active);
        all.addAll(paused);

        // 处理——逐个活动走四个部分：活动信息 / 活动与SKU / 上下架 / 库存
        int processed = process(all);

        log.info("活动缓存同步任务完成: 处理={}", processed);
    }

    /**
     * 逐个处理——四个部分：活动信息快照 / 活动与SKU（商品快照）/ 上下架 / 库存（判断活动状态）；
     * 单活动失败隔离，下轮自愈。
     */
    private int process(List<ActivityDTO> activities) {
        int processed = 0;
        for (ActivityDTO activity : activities) {
            try {
                String activityNo = activity.getActivityNo();
                List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
                if (rows.isEmpty() && activity.getActivityStatus() == ActivityStatus.PENDING) {
                    // 待开始无商品：什么都不写（同时避免为可被删除的空活动写入残留缓存）；进行中/已暂停空列表照写空快照
                    log.info("活动无商品，跳过: activityNo={}", activityNo);
                    continue;
                }
                // 部分一：活动信息快照
                writeInfo(activity);
                // 部分二：活动与SKU（商品快照，库存与上下架从中剔除）
                writeProductSnapshot(activityNo, rows);
                // 部分三：上下架
                writeShelfKeys(activityNo, rows);
                // 部分四：库存（判断活动状态，仅待开始）
                writeStockKeys(activity, rows);
                processed++;
            } catch (Exception e) {
                log.error("活动缓存同步失败: activityNo={}", activity.getActivityNo(), e);
            }
        }
        return processed;
    }

    /**
     * 是否进入预热窗口 [开始前 30 分钟, 开始时间]
     */
    private boolean isInWarmUpWindow(ActivityDTO activity, LocalDateTime now) {
        LocalDateTime activateMoment = LocalDateTime.of(activity.getStartDate(), activity.getStartTime());
        long secondsUntilStart = ChronoUnit.SECONDS.between(now, activateMoment);
        return secondsUntilStart >= 0 && secondsUntilStart <= WARM_UP_WINDOW_SECONDS;
    }

    /**
     * 覆盖写活动信息快照 → seckill:activity:info（Hash，field=activityNo）
     *
     * 每分钟重复执行、重复覆盖即刷新为最新快照，使暂停/激活/信息变更（含管理端手动操作）在运行期生效。
     */
    private void writeInfo(ActivityDTO activity) throws Exception {
        redisService.hSet(SeckillRedisKey.KEY_ACTIVITY_INFO, activity.getActivityNo(),
                objectMapper.writeValueAsString(activity));
    }

    /**
     * 覆盖写商品SKU列表快照 → seckill:activity:products:{activityNo}
     *
     * 快照只留静态目录：剔除库表带出的库存配额与上下架状态——二者由独立 key 承担，
     * 查询时由 service 从 key 拼装（免得快照里的旧值造成混淆）。
     */
    private void writeProductSnapshot(String activityNo, List<SeckillProductSkuDTO> rows) throws Exception {
        List<SeckillProductSkuDTO> snapshot = new ArrayList<>(rows.size());
        for (SeckillProductSkuDTO row : rows) {
            SeckillProductSkuDTO copy = new SeckillProductSkuDTO();
            BeanUtils.copyProperties(row, copy);
            copy.setActivityStock(null);
            copy.setShelfStatus(null);
            snapshot.add(copy);
        }
        redisService.set(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo),
                objectMapper.writeValueAsString(snapshot));
    }

    /**
     * 覆盖写在售名单 → seckill:sku:shelf:{activityNo}:{skuNo}（value=1 上架 / 0 下架）
     *
     * 声明式全量覆盖（下架同样写 0，不能跳过）；激活瞬间名单已就绪，无需等待首轮重建。
     */
    private void writeShelfKeys(String activityNo, List<SeckillProductSkuDTO> rows) {
        for (SeckillProductSkuDTO row : rows) {
            String shelfKey = String.format(SeckillRedisKey.KEY_SKU_SHELF, activityNo, row.getSkuNo());
            redisService.set(shelfKey, row.getShelfStatus() != null && row.getShelfStatus() == 1 ? "1" : "0");
        }
    }

    /**
     * 缺省初始化库存计数 → seckill:sku:stock:{activityNo}:{skuNo}
     *
     * 需判断活动状态：仅待开始缺省写入（仅 key 不存在时初始化，绝不覆盖运行期已扣减的实时值）；
     * 进行中 / 已暂停不触碰。
     */
    private void writeStockKeys(ActivityDTO activity, List<SeckillProductSkuDTO> rows) {
        if (activity.getActivityStatus() != ActivityStatus.PENDING) {
            return;
        }
        String activityNo = activity.getActivityNo();
        for (SeckillProductSkuDTO row : rows) {
            String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, row.getSkuNo());
            redisService.setIfAbsent(stockKey, String.valueOf(row.getActivityStock()));
        }
    }
}
