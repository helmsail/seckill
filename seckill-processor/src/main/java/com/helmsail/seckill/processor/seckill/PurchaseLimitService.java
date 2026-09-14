package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 限购额度扣减（Redis 原子操作）
 *
 * 扣减与 traceId 幂等标记同脚本原子写入：MQ 重投重放时跳过重复扣减（返回"已扣"视为成功）；
 * 回补为标记守卫的原子操作，回补失败仅记日志（需人工核对）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseLimitService {

    /** 限购键保留时长（秒）：从最后一次扣减起算，活动结束后自动回收 */
    private static final int PURCHASE_KEY_TTL_SECONDS = 30 * 24 * 60 * 60;

    /** 扣减标记有效期（秒）：与结果键防重放窗口（24h）齐平 */
    private static final int DEDUCT_MARK_TTL_SECONDS = 24 * 60 * 60;

    private final RedisService redisService;

    /**
     * 扣减限购额度（原子操作，含 traceId 幂等标记）
     *
     * @return true 表示扣减成立（不限购/本次扣减成功/本请求此前已扣减）；false 表示超过限购
     */
    public boolean deduct(String activityNo, String skuNo, String userId, int purchaseLimit,
                          int quantity, String traceId) {
        if (purchaseLimit <= 0) {
            // 不限购：不扣减、不写标记（回补侧以标记缺席自动跳过）
            return true;
        }
        String counterKey = String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId);
        String markKey = String.format(SeckillRedisKey.KEY_DEDUCT_LIMIT, traceId);
        Long result = redisService.executeLua(DEDUCT_LUA, List.of(counterKey, markKey),
                String.valueOf(purchaseLimit), String.valueOf(quantity),
                String.valueOf(PURCHASE_KEY_TTL_SECONDS), String.valueOf(DEDUCT_MARK_TTL_SECONDS));
        // 1=本次扣减成功；2=标记已存在（重投重放，视为已扣）；0=超过限购
        return result != null && result > 0;
    }

    /**
     * 恢复限购额度（标记守卫原子操作：标记存在才减回并删标记，重复执行无副作用）
     *
     * 不抛出异常：恢复失败仅记日志（需人工核对），调用方按完成处理。
     */
    public void restore(String activityNo, String skuNo, String userId, int quantity, String traceId) {
        try {
            String counterKey = String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId);
            String markKey = String.format(SeckillRedisKey.KEY_DEDUCT_LIMIT, traceId);
            redisService.executeLua(RESTORE_LUA, List.of(counterKey, markKey),
                    String.valueOf(quantity), String.valueOf(PURCHASE_KEY_TTL_SECONDS));
        } catch (Exception e) {
            log.error("恢复限购失败，需人工核对: activityNo={}, skuNo={}, userId={}, quantity={}, traceId={}",
                    activityNo, skuNo, userId, quantity, traceId, e);
        }
    }

    /** 扣减：标记已存在→返回 2（重放跳过）；超过限购→0；成功→计数增加并写标记（重置计数 TTL，防键永驻） */
    private static final String DEDUCT_LUA =
            "if redis.call('exists', KEYS[2]) == 1 then return 2 end " +
            "local current = tonumber(redis.call('get', KEYS[1])) or 0 " +
            "local limit = tonumber(ARGV[1]) " +
            "local quantity = tonumber(ARGV[2]) " +
            "if current + quantity > limit then return 0 end " +
            "redis.call('incrby', KEYS[1], quantity) " +
            "redis.call('expire', KEYS[1], tonumber(ARGV[3])) " +
            "redis.call('set', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[4])) " +
            "return 1";

    /**
     * 恢复：标记不存在→返回 -1（未扣过或已恢复，跳过）；计数减回（下界 0）并删标记（原子）
     *
     * 注意：Redis Lua 中 GET 不存在的键返回 false，tonumber(false) 得到 nil（nil 参与算术会异常），
     * 故以 `or 0` 兜底为 0。
     */
    private static final String RESTORE_LUA =
            "if redis.call('exists', KEYS[2]) == 0 then return -1 end " +
            "local current = tonumber(redis.call('get', KEYS[1])) or 0 " +
            "local target = current - tonumber(ARGV[1]) " +
            "if target < 0 then target = 0 end " +
            "redis.call('set', KEYS[1], target, 'EX', tonumber(ARGV[2])) " +
            "redis.call('del', KEYS[2]) " +
            "return 1";
}
