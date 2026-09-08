package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseLimitService {

    private final RedisService redisService;

    /**
     * 扣减限购额度（原子操作）
     *
     * @return true 表示扣减成功，false 表示超过限购
     */
    public boolean deduct(String activityNo, String skuNo, String userId, int purchaseLimit, int quantity) {
        if (purchaseLimit <= 0) {
            return true;
        }
        String key = String.format("seckill:purchase:%s:%s:%s", activityNo, skuNo, userId);
        Long result = redisService.executeLua(DEDUCT_LUA, Collections.singletonList(key),
                String.valueOf(purchaseLimit), String.valueOf(quantity));
        return result != null && result > 0;
    }

    /**
     * 恢复限购额度（原子操作）
     */
    public void restore(String activityNo, String skuNo, String userId, int quantity) {
        String key = String.format("seckill:purchase:%s:%s:%s", activityNo, skuNo, userId);
        redisService.executeLua(RESTORE_LUA, Collections.singletonList(key), String.valueOf(quantity));
    }

    private static final String DEDUCT_LUA =
            "local current = tonumber(redis.call('get', KEYS[1])) or 0 " +
            "local limit = tonumber(ARGV[1]) " +
            "local quantity = tonumber(ARGV[2]) " +
            "if current + quantity > limit then return 0 end " +
            "redis.call('incrby', KEYS[1], quantity) " +
            "return 1";

    private static final String RESTORE_LUA =
            "redis.call('decrby', KEYS[1], ARGV[1]) " +
            "return 1";
}
