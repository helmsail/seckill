package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 秒杀库存扣减（Redis 原子操作，运行期唯一权威）
 *
 * key 由 activityNo + skuNo 定位；库存计数仅由预热初始化、此处扣/补，刷新任务不触碰。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final RedisService redisService;

    public boolean deduct(String activityNo, String skuNo, int quantity) {
        String key = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo);
        Long result = redisService.executeLua(DEDUCT_LUA, Collections.singletonList(key), String.valueOf(quantity));
        return result != null && result > 0;
    }

    public void restore(String activityNo, String skuNo, int quantity) {
        String key = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo);
        String totalKey = String.format(SeckillRedisKey.KEY_SKU_STOCK_TOTAL, activityNo, skuNo);
        redisService.executeLua(RESTORE_LUA, List.of(key, totalKey), String.valueOf(quantity));
    }

    private static final String DEDUCT_LUA =
            "local stock = tonumber(redis.call('get', KEYS[1])) " +
            "if stock == nil then return 0 end " +
            "local quantity = tonumber(ARGV[1]) " +
            "if stock < quantity then return 0 end " +
            "redis.call('decrby', KEYS[1], quantity) " +
            "return 1";

    private static final String RESTORE_LUA =
            "local current = tonumber(redis.call('get', KEYS[1])) or 0 " +
            "local quantity = tonumber(ARGV[1]) " +
            "local target = current + quantity " +
            "local total = tonumber(redis.call('get', KEYS[2])) " +
            "if total ~= nil and target > total then return 0 end " +
            "redis.call('set', KEYS[1], target) " +
            "return 1";
}
