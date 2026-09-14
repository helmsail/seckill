package com.helmsail.seckill.service.seckill;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.activity.WeekBitmap;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.service.activity.ActivityQueryService;
import com.helmsail.seckill.service.config.SeckillConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀准入检查（六项合一）
 *
 * 限流 / 活动（抢跑拉黑 + 生效窗口）/ 黑名单 / 限购 / 在售 / 库存——“请求能否进入秒杀”的全部前置判定集中于此，
 * 按“用户 → 活动 → 商品 → 资源”顺序逐层收缩，方法序与 SeckillService 的调用顺序一致，逐项失败即抛对应业务码。
 * 所有检查均读取缓存快照做前置过滤，正确性以 processor 的 DB 权威终判为准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckService {

    private final RedisService redisService;
    private final RedissonClient redissonClient;
    private final SeckillConfig config;
    private final ActivityQueryService activityQueryService;

    /**
     * 限流检查（用户级令牌桶，全实例共享同一配额）
     */
    public void checkRateLimit(String userId) {
        String key = String.format(SeckillRedisKey.KEY_RATE_LIMIT, userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // trySetRate 仅在桶未初始化时写入（幂等），重复调用无影响
        rateLimiter.trySetRate(
                RateType.OVERALL,
                config.getRateLimit().getMaxCount(),
                Duration.ofSeconds(config.getRateLimit().getWindowSeconds()));
        // 令牌桶键无默认过期时间，按访问滑动设置 TTL，长期不活跃用户的限流键自动回收
        rateLimiter.expire(Duration.ofDays(1));

        if (!rateLimiter.tryAcquire()) {
            log.warn("用户被限流: userId={}", userId);
            throw new BizException(SeckillResultEnum.RATE_LIMITED);
        }
    }

    /**
     * 活动检查（抢跑拉黑 + 生效窗口）
     *
     * ① 抢跑拉黑：PENDING 且处于 [开始前 window-from-seconds, 开始前 window-to-seconds) 窗口内的
     * 请求视为脚本提前抢购——写入 Redis 黑名单（后续请求由 checkBlacklist 拦截）并拒绝；
     * 更贴近开始（含已开始）不再拉黑，避免误伤正常用户。
     * ② 生效窗口：状态=进行中 + 日期范围 + 当天时段 + 周位图，任一不满足即拒绝，避免无效请求白跑 MQ；
     * 运行期是否已结束由 processor 的 DB 状态终判兜底。
     */
    public void checkActivity(String activityNo, String userId) {
        ActivityDTO activity = activityQueryService.getActivityByNo(activityNo);
        if (activity == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_EFFECTIVE);
        }

        LocalDateTime now = LocalDateTime.now();

        // ① 抢跑拉黑：仅 PENDING + 窗口 [开始前 windowFrom, 开始前 windowTo)
        if (activity.getActivityStatus() == ActivityStatus.PENDING) {
            LocalDateTime start = LocalDateTime.of(activity.getStartDate(), activity.getStartTime());
            boolean inEarlyWindow = !now.isBefore(start.minusSeconds(config.getBlacklist().getWindowFromSeconds()))
                    && now.isBefore(start.minusSeconds(config.getBlacklist().getWindowToSeconds()));
            if (inEarlyWindow) {
                // 写入黑名单（TTL 到期自动解除），后续请求由 checkBlacklist 拦截
                redisService.set(String.format(SeckillRedisKey.KEY_BLACKLIST, userId),
                        "活动未开始时请求秒杀", config.getBlacklist().getExpireSeconds(), TimeUnit.SECONDS);
                log.warn("抢跑拉黑: userId={}, activityNo={}", userId, activityNo);
                throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR);
            }
        }

        // ② 生效窗口：进行中 + 日期范围 + 当天时段 + 周位图
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        boolean effective = activity.getActivityStatus() == ActivityStatus.ACTIVE
                && !today.isBefore(activity.getStartDate())
                && !today.isAfter(activity.getEndDate())
                && !time.isBefore(activity.getStartTime())
                && !time.isAfter(activity.getEndTime())
                && WeekBitmap.isActive(activity.getWeekBitmap(), today.getDayOfWeek());
        if (!effective) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_EFFECTIVE);
        }
    }

    /**
     * 黑名单检查
     */
    public void checkBlacklist(String userId) {
        String key = String.format(SeckillRedisKey.KEY_BLACKLIST, userId);
        if (Boolean.TRUE.equals(redisService.hasKey(key))) {
            throw new BizException(SeckillResultEnum.BLACKLISTED);
        }
    }

    /**
     * 限购检查（Redis 计数只读否决）
     *
     * 已达限购直接拒绝；计数含在途延迟（排队中的消息尚未扣减），并发下可能漏放，
     * 真实扣减以 processor 的 Lua 原子判定为准。
     */
    public void checkPurchaseLimit(String activityNo, String skuNo, String userId, int quantity) {
        Integer purchaseLimit = activityQueryService.getSkuPurchaseLimit(activityNo, skuNo);
        if (purchaseLimit == null || purchaseLimit <= 0) {
            return;
        }
        String used = redisService.get(String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId));
        int current = used == null ? 0 : Integer.parseInt(used);
        if (current + quantity > purchaseLimit) {
            throw new BizException(SeckillResultEnum.PURCHASE_LIMITED);
        }
    }

    /**
     * 在售检查（Redis 在售键）
     *
     * 键滞后最多导致少量请求白跑，放行后由 processor 权威终判。
     */
    public void checkSkuOnShelf(String activityNo, String skuNo) {
        String key = String.format(SeckillRedisKey.KEY_SKU_SHELF, activityNo, skuNo);
        if (!"1".equals(redisService.get(key))) {
            throw new BizException(SeckillResultEnum.SKU_OFF_SHELF);
        }
    }

    /**
     * 库存检查（Redis 快照只读否决）
     *
     * 真实扣减以 processor 的 Lua 原子扣减为准。
     */
    public void checkStock(String activityNo, String skuNo, int quantity) {
        Integer stock = activityQueryService.getSkuStock(activityNo, skuNo);
        if (stock == null || stock < quantity) {
            throw new BizException(SeckillResultEnum.STOCK_INSUFFICIENT);
        }
    }
}
