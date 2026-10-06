package com.helmsail.seckill.service.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.tracing.UserContext;
import com.helmsail.seckill.service.config.SeckillConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀准入检查（七项合一）
 *
 * 限流 / 活动（抢跑拉黑 + 时间窗）/ 黑名单 / 活动限购 / SKU 在售 / SKU 限购 / SKU 库存——“请求能否进入秒杀”的全部前置判定集中于此，
 * 按“用户 → 活动 → 商品 → 资源”顺序逐层收缩，方法序与 SeckillService 的调用顺序一致，逐项失败即抛对应业务码。
 * 所有检查均直读 Redis（快照 / 键）做前置过滤，用户身份自请求上下文（UserContext）就地获取；
 * 正确性以 processor 的 DB 权威终判为准。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckService {

    private final RedisService redisService;
    private final RedissonClient redissonClient;
    private final SeckillConfig config;
    private final ObjectMapper objectMapper;

    /**
     * 限流检查（用户级令牌桶，全实例共享同一配额）
     */
    public void checkRateLimit() {
        String userId = UserContext.currentUserId();
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
     * 活动检查 = 防抢跑（安全）+ 准入（状态 + 时间窗）
     *
     * 时间窗以活动的开始时刻 a 与结束时刻 b 为基准：now ∈ [a - windowRight, b] 放行，
     * [a - windowLeft, a - windowRight) 拉黑并拒；日期范围与周位图不在读侧重复校验
     * （按“写侧筛选、读侧信快照”的约定）；真实生效以 processor 的 DB 状态终判为准。
     */
    public void checkActivity(String activityNo) {
        String userId = UserContext.currentUserId();
        // 读活动：miss / 解析失败均按不可用拒绝
        String json = redisService.hGet(SeckillRedisKey.KEY_ACTIVITY_INFO, activityNo);
        ActivityDTO activity = null;
        if (json != null) {
            try {
                activity = objectMapper.readValue(json, ActivityDTO.class);
            } catch (Exception e) {
                log.error("活动信息解析失败: activityNo={}", activityNo, e);
            }
        }
        if (activity == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_EFFECTIVE);
        }

        // 状态：仅进行中 / 待开始可继续（不细分具体状态），其余拒绝
        ActivityStatus status = activity.getActivityStatus();
        if (status != ActivityStatus.ACTIVE && status != ActivityStatus.PENDING) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_EFFECTIVE);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.of(activity.getStartDate(), activity.getStartTime());
        LocalDateTime end = LocalDateTime.of(activity.getEndDate(), activity.getEndTime());
        LocalDateTime blacklistFrom = start.minusSeconds(config.getBlacklist().getWindowFromSeconds());
        LocalDateTime openFrom = start.minusSeconds(config.getBlacklist().getWindowToSeconds());

        // ① 防抢跑（安全侧）：[开始前 windowLeft 秒, 开始前 windowRight 秒) 视为脚本提前抢购——
        //    写入黑名单（TTL 到期自动解除，后续请求由 checkBlacklist 拦截）并拒绝；
        //    windowLeft 覆盖预热期（预热只发生在开始前 30 分钟内，提前来的只可能是脚本或探测）
        if (!now.isBefore(blacklistFrom) && now.isBefore(openFrom)) {
            redisService.set(String.format(SeckillRedisKey.KEY_BLACKLIST, userId),
                    "活动未开始时请求秒杀", config.getBlacklist().getExpireSeconds(), TimeUnit.SECONDS);
            log.warn("抢跑拉黑: userId={}, activityNo={}", userId, activityNo);
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR);
        }

        // ② 开放区 [开始前 windowRight 秒, 结束时刻]：放行（含准点容差与快照滞后过渡态，
        //    真实生效由 processor 的 DB 终判兜底）；其余（更早 / 晚于结束）一律拒绝
        if (now.isBefore(openFrom) || now.isAfter(end)) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_EFFECTIVE);
        }
    }

    /**
     * 黑名单检查
     */
    public void checkBlacklist() {
        String userId = UserContext.currentUserId();
        String key = String.format(SeckillRedisKey.KEY_BLACKLIST, userId);
        if (Boolean.TRUE.equals(redisService.hasKey(key))) {
            throw new BizException(SeckillResultEnum.BLACKLISTED);
        }
    }

    /**
     * 活动限购检查（活动维度每人合计限量；Redis 计数只读否决）
     *
     * 上限直读活动 Hash 的 field（一 field 一条，无需独立键）；miss/坏数据按不限购宽容，
     * 活动有效性由 checkActivity 把关；计数含在途延迟（排队中的消息尚未扣减），
     * 真实扣减以 processor 的 Lua 原子判定为准。
     */
    public void checkActivityPurchaseLimit(String activityNo, int quantity) {
        String userId = UserContext.currentUserId();
        // 该活动每人合计限量（0=不限购）——读活动 Hash field
        int activityLimit = 0;
        try {
            String activityJson = redisService.hGet(SeckillRedisKey.KEY_ACTIVITY_INFO, activityNo);
            if (activityJson != null) {
                ActivityDTO dto = objectMapper.readValue(activityJson, ActivityDTO.class);
                activityLimit = dto.getPurchaseLimit() == null ? 0 : dto.getPurchaseLimit();
            }
        } catch (Exception e) {
            log.warn("活动级限购读取失败（按不限购处理）: activityNo={}", activityNo, e);
        }
        if (activityLimit > 0) {
            String activityUsed = redisService.get(
                    String.format(SeckillRedisKey.KEY_ACTIVITY_PURCHASE_LIMIT, activityNo, userId));
            int activityCurrent = activityUsed == null ? 0 : Integer.parseInt(activityUsed);
            if (activityCurrent + quantity > activityLimit) {
                throw new BizException(SeckillResultEnum.PURCHASE_LIMITED);
            }
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
     * SKU 限购检查（该活动该 SKU 限量；Redis 计数只读否决）
     *
     * 上限与已购计数直读 Redis 独立键（校验数据不吃展示缓存窗口）；
     * 计数含在途延迟（排队中的消息尚未扣减），并发下可能漏放，
     * 真实扣减以 processor 的 Lua 原子判定为准。
     */
    public void checkSkuPurchaseLimit(String activityNo, String skuNo, int quantity) {
        String userId = UserContext.currentUserId();
        String quota = redisService.get(String.format(SeckillRedisKey.KEY_SKU_QUOTA, activityNo, skuNo));
        int purchaseLimit = quota == null ? 0 : Integer.parseInt(quota);
        if (purchaseLimit <= 0) {
            return;
        }
        String used = redisService.get(String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId));
        int current = used == null ? 0 : Integer.parseInt(used);
        if (current + quantity > purchaseLimit) {
            throw new BizException(SeckillResultEnum.PURCHASE_LIMITED);
        }
    }

    /**
     * 库存检查（Redis 键只读否决，直读不经缓存）
     *
     * 真实扣减以 processor 的 Lua 原子扣减为准。
     */
    public void checkStock(String activityNo, String skuNo, int quantity) {
        String stock = redisService.get(String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo));
        int current = stock == null ? 0 : Integer.parseInt(stock);
        if (current < quantity) {
            throw new BizException(SeckillResultEnum.STOCK_INSUFFICIENT);
        }
    }

}
