package com.helmsail.seckill.service.check;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.redis.SeckillServiceKey;
import com.helmsail.seckill.service.activity.ActivityQueryService;
import com.helmsail.seckill.service.config.SeckillConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistCheckService {

    private final RedisService redisService;
    private final SeckillConfig config;
    private final ActivityQueryService activityQueryService;

    public boolean check(String userId) {
        if (!config.getCheck().isBlacklist()) {
            return true;
        }
        return !isBlacklisted(userId);
    }

    public boolean isBlacklisted(String userId) {
        String key = String.format(SeckillServiceKey.KEY_BLACKLIST, userId);
        Boolean exists = redisService.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    public boolean checkActivityStatus(String activityNo, String userId) {
        if (!config.getCheck().isActivityStatus()) {
            return true;
        }

        ActivityDTO activity = activityQueryService.getActivityByNo(activityNo);
        if (activity == null) {
            return false;
        }

        if (activity.getActivityStatus() == ActivityStatus.ACTIVE) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activityStart = activity.getStartTime();
        int windowBefore = config.getBlacklist().getWindowBeforeSeconds();
        int windowAfter = config.getBlacklist().getWindowAfterSeconds();

        if (now.isAfter(activityStart.minusSeconds(windowBefore))
                && now.isBefore(activityStart.plusSeconds(windowAfter))) {
            addBlacklist(userId, "活动未开始时请求秒杀");
            return false;
        }

        return false;
    }

    public void addBlacklist(String userId, String reason) {
        String key = String.format(SeckillServiceKey.KEY_BLACKLIST, userId);
        int expireSeconds = config.getBlacklist().getExpireSeconds();
        if (expireSeconds > 0) {
            redisService.set(key, reason, expireSeconds, TimeUnit.SECONDS);
        } else {
            redisService.set(key, reason);
        }
        log.warn("用户加入黑名单: userId={}, reason={}", userId, reason);
    }

    public void removeBlacklist(String userId) {
        String key = String.format(SeckillServiceKey.KEY_BLACKLIST, userId);
        redisService.delete(key);
        log.info("用户移出黑名单: userId={}", userId);
    }
}
