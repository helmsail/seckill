package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityDubboService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.activity.WeekBitmap;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDubboService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 活动缓存同步任务
 *
 * 将"窗口内待开始 / 进行中 / 已暂停"活动从 base 库搬进 Redis：获取 → 过滤合并 → 逐个处理。
 *
 * 1. 获取：PENDING/ACTIVE/PAUSED 各拉一次；
 * 2. 过滤合并：待开始只留进入预热窗口的（三合一：今天可售 + 临近今天的开场；窗口外不缓存——远期/非可售日不占缓存），加进行中、已暂停合成处理列表；
 * 3. 逐个活动处理（一活动的数据一趟内完成）：
 *    - 覆写活动信息 Hash（覆盖即刷新、新增即补充），使暂停/激活/信息变更（含管理端手动操作）在运行期生效；
 *    - 逐 SKU 写运行态键：库存（仅待开始 setIfAbsent 缺省初始化，绝不覆盖运行期已扣减的实时值）→
 *      上下架（声明式覆盖，下架同样写 0，激活瞬间名单已就绪）→ 限购上限（静态配置，删除重加可能改值）；
 *    - 全体 SKU 处理完毕后整体化为 JSON 写入快照键——运行态字段已就地剔除，快照只留静态目录（免得旧值混淆）。
 *
 * 库存仅在本任务“待开始”分支被写入，其余任何分支与状态永不触碰库存键——
 * 运行期实时值只被扣减/回补修改；活动终态清理见 StockCleanupJobHandler（壳子，待实现）。
 * TTL 政策（三态内每轮续期，关闭后自然回收）：快照/上下架 7 天、限购上限 30 天（淘汰方向为“放宽”，须留停摆余量）；
 * 库存键无 TTL（缺失=全拒且无补充机制，绝不淘汰）；活动 Hash field 无法按 field 过期，由 ActivityInfoCleanupJobHandler 清理。
 * 正确性兜底：缓存残留/缺失均由 processor 的 DB 终判收口（状态流转见 ActivityStatusJobHandler，仅操作 DB 不碰缓存）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityCacheJobHandler {

    private static final int WARM_UP_WINDOW_SECONDS = 30 * 60;

    /** 运行态键 TTL（秒）：快照/上下架；刷新任务每轮续期，7 天=任务停摆容忍余量 */
    private static final int RUNTIME_KEY_TTL_SECONDS = 7 * 24 * 60 * 60;

    /** 限购上限键 TTL（秒）：淘汰方向为“放宽”（缺失=不限购）且无限购 DB 终判，须给足停摆余量 */
    private static final int QUOTA_KEY_TTL_SECONDS = 30 * 24 * 60 * 60;

    @DubboReference
    private ActivityDubboService activityService;

    @DubboReference
    private SeckillProductSkuDubboService seckillProductSkuService;

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
            if (inPreheatWindow(activity, now)) {
                all.add(activity);
            }
        }
        all.addAll(active);
        all.addAll(paused);

        // 处理——逐个活动：覆写 Hash → 逐 SKU 运行态键 → 整体快照
        int processed = process(all);

        log.info("活动缓存同步任务完成: 处理={}", processed);
    }

    /**
     * 逐个活动处理——覆写 Hash → 逐 SKU 写运行态键（库存仅待开始）→ 整体化为 JSON 快照；
     * 单活动失败隔离，下轮自愈。
     */
    private int process(List<ActivityDTO> activities) {
        int processed = 0;
        for (ActivityDTO activity : activities) {
            try {
                String activityNo = activity.getActivityNo();
                boolean pending = activity.getActivityStatus() == ActivityStatus.PENDING;

                // 查询该活动的 SKU
                List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
                if (rows.isEmpty() && pending) {
                    // 待开始无商品：什么都不写（同时避免为可被删除的空活动写入残留缓存）；进行中/已暂停空列表照写空快照
                    log.info("活动无商品，跳过: activityNo={}", activityNo);
                    continue;
                }

                // 覆写活动信息 Hash（覆盖即刷新、新增即补充）
                redisService.hSet(SeckillRedisKey.KEY_ACTIVITY_INFO, activityNo,
                        objectMapper.writeValueAsString(activity));

                // 逐 SKU：库存（仅待开始）→ 上下架 → 限购上限 → 就地剔除（供快照）
                for (SeckillProductSkuDTO row : rows) {
                    String skuNo = row.getSkuNo();
                    if (pending) {
                        redisService.setIfAbsent(String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo),
                                String.valueOf(row.getActivityStock()));
                    }
                    redisService.set(String.format(SeckillRedisKey.KEY_SKU_SHELF, activityNo, skuNo),
                            row.getShelfStatus() != null && row.getShelfStatus() == 1 ? "1" : "0",
                            RUNTIME_KEY_TTL_SECONDS, TimeUnit.SECONDS);
                    redisService.set(String.format(SeckillRedisKey.KEY_SKU_QUOTA, activityNo, skuNo),
                            String.valueOf(row.getPurchaseLimit() == null ? 0 : row.getPurchaseLimit()),
                            QUOTA_KEY_TTL_SECONDS, TimeUnit.SECONDS);
                    // 运行态值已入键，就地剔除——快照只留静态目录（免得旧值混淆）
                    row.setActivityStock(null);
                    row.setShelfStatus(null);
                }

                // 全体 SKU 处理完毕，整体化为 JSON 写入快照键（带 TTL：三态内续期，关闭后自然回收）
                redisService.set(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo),
                        objectMapper.writeValueAsString(rows), RUNTIME_KEY_TTL_SECONDS, TimeUnit.SECONDS);
                processed++;
            } catch (Exception e) {
                log.error("活动缓存同步失败: activityNo={}", activity.getActivityNo(), e);
            }
        }
        return processed;
    }

    /**
     * 待开始活动是否进入预热窗口：今天可售（日期 ∩ 周位图）且临近今天的开场时段
     *
     * 三合判定：今天∈[startDate, endDate] ∧ 位图含今天 ∧ now∈[今天 startTime−30min, 今天 startTime]
     * ——“开场”按当天重算（多场活动的后续场次由 ACTIVE 全量刷新兜住，无需再次预热）；
     * ACTIVE / PAUSED 不受窗口限制，每轮全量处理。
     */
    private boolean inPreheatWindow(ActivityDTO activity, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        // 日期：今天在活动日期范围内
        if (today.isBefore(activity.getStartDate()) || today.isAfter(activity.getEndDate())) {
            return false;
        }
        // 星期：周位图含今天
        if (!WeekBitmap.isActive(activity.getWeekBitmap(), today.getDayOfWeek())) {
            return false;
        }
        // 时段：临近当天开场（startTime）前 30 分钟窗口
        long secondsUntilOpen = ChronoUnit.SECONDS.between(now, LocalDateTime.of(today, activity.getStartTime()));
        return secondsUntilOpen >= 0 && secondsUntilOpen <= WARM_UP_WINDOW_SECONDS;
    }

}
