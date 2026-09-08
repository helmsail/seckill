package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final RedisService redisService;

    public boolean deduct(String skuNo, int quantity) {
        String key = String.format("seckill:sku:stock:%s", skuNo);
        Long result = redisService.executeLua(DEDUCT_LUA, Collections.singletonList(key), String.valueOf(quantity));
        return result != null && result > 0;
    }

    public void restore(String skuNo, int quantity) {
        String key = String.format("seckill:sku:stock:%s", skuNo);
        redisService.executeLua(RESTORE_LUA, Collections.singletonList(key), String.valueOf(quantity));
    }

    private static final String DEDUCT_LUA =
            "local stock = tonumber(redis.call('get', KEYS[1])) " +
            "if stock == nil then return 0 end " +
            "local quantity = tonumber(ARGV[1]) " +
            "if stock < quantity then return 0 end " +
            "redis.call('decrby', KEYS[1], quantity) " +
            "return 1";

    private static final String RESTORE_LUA =
            "redis.call('incrby', KEYS[1], ARGV[1]) " +
            "return 1";
}
