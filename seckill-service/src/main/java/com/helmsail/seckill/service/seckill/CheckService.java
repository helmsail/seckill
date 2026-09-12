package com.helmsail.seckill.service.seckill;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.activity.ActivityWindows;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.service.activity.ActivityQueryService;
import com.helmsail.seckill.service.support.SeckillConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀准入检查（六项合一）
 *
 * 限流 / 黑名单 / 活动状态 / 时间窗口 / 在售 / 库存——“请求能否进入秒杀”的全部前置判定集中于此。
 * 所有检查均为前置过滤（缓存放行），正确性以 processor 的 DB 权威终判为准；每项均可通过
 * seckill.check.* 配置单独开关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckService {

    private static final String SISMEMBER_LUA = "return redis.call('sismember', KEYS[1], ARGV[1])";

    private final RedisService redisService;
    private final RedissonClient redissonClient;
    private final SeckillConfig config;
    private final ActivityQueryService activityQueryService;

    /**
     * 限流检查（用户级令牌桶）
     */
    public boolean checkRateLimit(String userId) {
        if (!config.getCheck().isRateLimit()) {
            return true;
        }
        String key = String.format(SeckillRedisKey.KEY_RATE_LIMIT, userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        // trySetRate 是幂等的，重复调用无影响
        rateLimiter.trySetRate(
                RateType.OVERALL,
                config.getRateLimit().getMaxCount(),
                config.getRateLimit().getWindowSeconds(),
                RateIntervalUnit.SECONDS);
        // 令牌桶键无默认过期时间，按访问滑动设置 TTL，长期不活跃用户的限流键自动回收
        rateLimiter.expire(Duration.ofDays(1));

        boolean acquired = rateLimiter.tryAcquire();
        if (!acquired) {
            log.warn("用户被限流: userId={}", userId);
        }
        return acquired;
    }

    /**
     * 黑名单检查
     */
    public boolean checkBlacklist(String userId) {
        if (!config.getCheck().isBlacklist()) {
            return true;
        }
        return !isBlacklisted(userId);
    }

    /**
     * 活动状态检查
     *
     * 仅对 PENDING + 窗口期内的请求拒绝并拉黑（提前抢跑防刷），其余一概通过。
     */
    public boolean checkActivityStatus(String activityNo, String userId) {
        if (!config.getCheck().isActivityStatus()) {
            return true;
        }

        ActivityDTO activity = activityQueryService.getActivityByNo(activityNo);
        if (activity == null) {
            return true;
        }

        // 仅对待开始状态进行窗口期检测
        if (activity.getActivityStatus() != ActivityStatus.PENDING) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activityStart = LocalDateTime.of(activity.getStartDate(), activity.getStartTime());
        int windowBefore = config.getBlacklist().getWindowBeforeSeconds();
        int windowAfter = config.getBlacklist().getWindowAfterSeconds();

        if (now.isAfter(activityStart.minusSeconds(windowBefore))
                && now.isBefore(activityStart.plusSeconds(windowAfter))) {
            addBlacklist(userId, "活动未开始时请求秒杀");
            return false;
        }

        return true;
    }

    /**
     * 时间窗口检查（缓存数据，仅作前置过滤）
     *
     * 正确性以 processor 的 DB 权威终判为准（ActivityWindows 同一套规则）。
     */
    public boolean checkEffectiveWindow(String activityNo) {
        return ActivityWindows.isInEffectiveWindow(activityQueryService.getActivityByNo(activityNo), LocalDateTime.now());
    }

    /**
     * 在售检查（Redis 名单，仅作前置过滤）
     *
     * 正确性以 processor 的 DB 权威状态终判为准；名单滞后最多导致少量请求白跑。
     */
    public boolean checkSkuOnShelf(String activityNo, String skuNo) {
        String key = String.format(SeckillRedisKey.KEY_ACTIVITY_SHELF, activityNo);
        Long result = redisService.executeLua(SISMEMBER_LUA, Collections.singletonList(key), skuNo);
        return result != null && result > 0;
    }

    /**
     * 库存检查
     *
     * 只读 Redis 快照快速否决；真实扣减以 processor 的 Lua 原子扣减为准。
     */
    public boolean checkStock(String activityNo, String skuNo, int quantity) {
        if (!config.getCheck().isStock()) {
            return true;
        }
        Integer stock = activityQueryService.getSkuStock(activityNo, skuNo);
        return stock != null && stock >= quantity;
    }

    /**
     * 拉黑用户（活动状态防刷与管理场景）
     */
    public void addBlacklist(String userId, String reason) {
        String key = String.format(SeckillRedisKey.KEY_BLACKLIST, userId);
        int expireSeconds = config.getBlacklist().getExpireSeconds();
        if (expireSeconds > 0) {
            redisService.set(key, reason, expireSeconds, TimeUnit.SECONDS);
        } else {
            redisService.set(key, reason);
        }
        log.warn("用户加入黑名单: userId={}, reason={}", userId, reason);
    }

    /**
     * 移出黑名单
     */
    public void removeBlacklist(String userId) {
        String key = String.format(SeckillRedisKey.KEY_BLACKLIST, userId);
        redisService.delete(key);
        log.info("用户移出黑名单: userId={}", userId);
    }

    private boolean isBlacklisted(String userId) {
        String key = String.format(SeckillRedisKey.KEY_BLACKLIST, userId);
        Boolean exists = redisService.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}
