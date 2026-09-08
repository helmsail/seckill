package com.helmsail.seckill.service.check;

import com.helmsail.seckill.service.config.SeckillConfig;
import com.helmsail.seckill.service.constant.SeckillServiceKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitCheckService {

    private final RedissonClient redissonClient;
    private final SeckillConfig config;
    private final Map<String, Boolean> initialized = new ConcurrentHashMap<>();

    public boolean check(String userId) {
        if (!config.getCheck().isRateLimit()) {
            return true;
        }
        return !isRateLimited(userId);
    }

    public boolean isRateLimited(String userId) {
        String key = String.format(SeckillServiceKey.KEY_RATE_LIMIT, userId);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);

        initialized.computeIfAbsent(key, k -> {
            rateLimiter.trySetRate(
                    RateType.OVERALL,
                    config.getRateLimit().getMaxCount(),
                    config.getRateLimit().getWindowSeconds(),
                    RateIntervalUnit.SECONDS);
            return true;
        });

        boolean acquired = rateLimiter.tryAcquire();
        if (!acquired) {
            log.warn("用户被限流: userId={}", userId);
        }
        return !acquired;
    }
}
