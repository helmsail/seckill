package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseLimitService {

    /** 限购键保留时长（秒）：从最后一次扣减起算，活动结束后自动回收 */
    private static final int PURCHASE_KEY_TTL_SECONDS = 30 * 24 * 60 * 60;

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
        String key = String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId);
        Long result = redisService.executeLua(DEDUCT_LUA, Collections.singletonList(key),
                String.valueOf(purchaseLimit), String.valueOf(quantity),
                String.valueOf(PURCHASE_KEY_TTL_SECONDS));
        return result != null && result > 0;
    }

    /**
     * 恢复限购额度（原子操作）
     *
     * 键不存在（从未扣减或已过期）时不处理；恢复后重置 TTL 与扣减保持一致。
     */
    public void restore(String activityNo, String skuNo, String userId, int quantity) {
        String key = String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId);
        redisService.executeLua(RESTORE_LUA, Collections.singletonList(key),
                String.valueOf(quantity), String.valueOf(PURCHASE_KEY_TTL_SECONDS));
    }

    private static final String DEDUCT_LUA =
            "local current = tonumber(redis.call('get', KEYS[1])) or 0 " +
            "local limit = tonumber(ARGV[1]) " +
            "local quantity = tonumber(ARGV[2]) " +
            "if current + quantity > limit then return 0 end " +
            "redis.call('incrby', KEYS[1], quantity) " +
            "redis.call('expire', KEYS[1], tonumber(ARGV[3])) " +
            "return 1";

    /** 恢复额度（键不存在跳过；下界 0；重置 TTL，避免 set 清除过期时间造成键永驻） */
    private static final String RESTORE_LUA =
            "local current = redis.call('get', KEYS[1]) " +
            "if current == nil then return -1 end " +
            "local target = tonumber(current) - tonumber(ARGV[1]) " +
            "if target < 0 then target = 0 end " +
            "redis.call('set', KEYS[1], target, 'EX', tonumber(ARGV[2])) " +
            "return target";
}
