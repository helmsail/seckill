package com.helmsail.seckill.job.handler;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityDubboService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDubboService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.support.api.sku.SkuDubboService;
import com.helmsail.seckill.support.api.sku.StockChangeItem;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动库存清理任务（活动终态清理：结余取走归还主域）
 *
 * 扫已关闭活动：结束超安全期 → 逐 SKU GETDEL 原子取走结余（取走即记日志=账本）
 * → 整批归还主域 SKU（域内单事务：整批还/零还）。
 *
 * 为何安全期后仍处理：关单回补会把已删的库存键"重建"（迟到增量），
 * 下一轮任务再取再还 → 主域按轮次收敛（每轮各取各的量、各自准确），无需"无未终局订单"前置。
 * 为何 GETDEL 而非 GET+DEL：结余恰被取走一次——失败方向=漏加（值已在日志，告警人工补），
 * 绝不重复累加（主域归还为 stock = stock + n，非幂等）。
 *
 * 临时处理定位（生产增强方向）：归还台账幂等记账、与订单/支付流水对账、自动补偿重试。
 * 快照/上下架/限购上限由 TTL 自然回收；活动信息 field 见 ActivityInfoCleanupJobHandler。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockCleanupJobHandler {

    /** 安全期（小时）：结束超该时长才归还（降低"分多次归还"噪音；收敛由周期+原子取走保证，不依赖它） */
    private static final int SAFETY_WINDOW_HOURS = 24;

    @DubboReference
    private ActivityDubboService activityService;

    @DubboReference
    private SeckillProductSkuDubboService seckillProductSkuService;

    /** 主域 SKU 库存归还（累加写，非幂等；禁自动重试防重复累加） */
    @DubboReference(retries = 0)
    private SkuDubboService skuService;

    private final RedisService redisService;

    @XxlJob("stockCleanupJob")
    public void execute() {
        List<ActivityDTO> closed = activityService.listByStatus(ActivityStatus.CLOSED);
        LocalDateTime safeBefore = LocalDateTime.now().minusHours(SAFETY_WINDOW_HOURS);

        int restoredActivities = 0;
        int restoredSkus = 0;
        int failed = 0;

        for (ActivityDTO activity : closed) {
            String activityNo = activity.getActivityNo();
            if (LocalDateTime.of(activity.getEndDate(), activity.getEndTime()).isAfter(safeBefore)) {
                continue;
            }
            try {
                List<SeckillProductSkuDTO> skus = seckillProductSkuService.listByActivityNo(activityNo);
                if (skus.isEmpty()) {
                    continue;
                }

                List<StockChangeItem> items = new ArrayList<>();
                for (SeckillProductSkuDTO sku : skus) {
                    String key = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, sku.getSkuNo());
                    // 原子取走：同一份结余恰好归还一次；null=键不在（已归还/从未初始化/迟到回补尚未发生）
                    String remaining = redisService.getAndDelete(key);
                    if (remaining == null) {
                        continue;
                    }
                    // 先记日志（取走即账本）：后续解析/归还失败时，人工凭此核对补还
                    log.warn("库存结余取走归还主域: activityNo={}, skuNo={}, remaining={}",
                            activityNo, sku.getSkuNo(), remaining);
                    items.add(new StockChangeItem(sku.getSkuNo(), Integer.parseInt(remaining)));
                }
                if (items.isEmpty()) {
                    continue;
                }

                // 整批归还（域内单事务：整批还/零还）；失败=值已取走不会重复，按上条日志人工补
                skuService.batchAddStock(items);
                restoredActivities++;
                restoredSkus += items.size();
            } catch (Exception e) {
                failed++;
                log.error("活动库存归还失败，需人工核对: activityNo={}", activityNo, e);
            }
        }
        log.info("活动库存归还任务完成: 归还活动={}, 归还SKU={}, 失败活动={}", restoredActivities, restoredSkus, failed);
    }
}
