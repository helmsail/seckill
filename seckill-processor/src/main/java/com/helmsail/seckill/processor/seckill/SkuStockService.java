package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SKU 秒杀库存服务（限购两层 + 库存：原子扣减 / 标记守卫回补）
 *
 * 管理一笔购买涉及的三个数字：活动级限购计数（每人该活动合计）、SKU 级限购计数（每人该 SKU）、
 * SKU 库存。限购为静态配置（快照由预热/刷新任务维护，不吃 DB）；库存为运行期唯一权威。
 *
 * deduct（扣减）：三层判定与扣减在同一 Lua 内原子完成（脚本内"读上限/计数/库存 → 比对 → 增减 → 写借据"）：
 *   任一层不足即整体拒绝（限购超限 → "超过限购"；库存不足 → "库存不足"）——绝无"扣了一半"的中间态、
 *   无需补偿；借据键存在即"已扣"（重投重放跳过）。
 * restore（回补）：借据销账——活动/SKU 层凭"计数键存在"减回、库存加回，最后删借据键（重复执行无副作用）；
 *   建单在 DB 侧无法并入脚本，建单失败/终局/关单的回补均走本方法；回补失败仅记日志（需人工核对）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkuStockService {

    /** 限购计数键保留时长（秒）：从最后一次扣减起算，活动结束后自动回收 */
    private static final int PURCHASE_KEY_TTL_SECONDS = 30 * 24 * 60 * 60;

    /** 扣减借据有效期（秒）：与结果键防重放窗口（24h）齐平 */
    private static final int DEDUCT_RECEIPT_TTL_SECONDS = 24 * 60 * 60;

    private final RedisService redisService;

    /**
     * 库存扣减（限购校验前置：活动限购 → SKU 限购 → 库存，三层一口原子判定与扣减）
     *
     * @return null 表示扣减成立（不限购/本次扣减成功/本请求此前已扣减）；非 null 为失败原因（"超过限购"/"库存不足"）
     */
    public String deduct(String activityNo, String skuNo, String userId, int quantity, String traceId) {
        Long result = redisService.executeLua(DEDUCT_LUA,
                List.of(SeckillRedisKey.KEY_ACTIVITY_INFO,
                        String.format(SeckillRedisKey.KEY_ACTIVITY_PURCHASE_LIMIT, activityNo, userId),
                        String.format(SeckillRedisKey.KEY_SKU_QUOTA, activityNo, skuNo),
                        String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId),
                        String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo),
                        String.format(SeckillRedisKey.KEY_DEDUCT_RECEIPT, traceId)),
                activityNo, String.valueOf(quantity),
                String.valueOf(PURCHASE_KEY_TTL_SECONDS), String.valueOf(DEDUCT_RECEIPT_TTL_SECONDS));
        // 1=本次扣减成功；2=借据已存在（重投重放，视为已扣）；0=超过限购（任一层）；-1=库存不足
        if (result != null && result > 0) {
            return null;
        }
        return result != null && result == -1 ? "库存不足" : "超过限购";
    }

    /**
     * 库存回补（含限购两层；借据销账：凭计数键存在逐层减回、库存加回，重复执行无副作用）
     *
     * 限购两层减回（下界 0、刷新计数 TTL）、库存加回；失败仅记日志（需人工核对），不抛出异常。
     */
    public void restore(String activityNo, String skuNo, String userId, int quantity, String traceId) {
        try {
            redisService.executeLua(RESTORE_LUA,
                    List.of(String.format(SeckillRedisKey.KEY_ACTIVITY_PURCHASE_LIMIT, activityNo, userId),
                            String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId),
                            String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo),
                            String.format(SeckillRedisKey.KEY_DEDUCT_RECEIPT, traceId)),
                    String.valueOf(quantity), String.valueOf(PURCHASE_KEY_TTL_SECONDS));
        } catch (Exception e) {
            log.error("回补秒杀库存失败，需人工核对: activityNo={}, skuNo={}, userId={}, quantity={}, traceId={}",
                    activityNo, skuNo, userId, quantity, traceId, e);
        }
    }

    /**
     * 三层一口原子扣减：读上限/计数/库存 → 判定 → 增减 → 写借据，全在脚本内完成（无"读后再扣"间隙）
     *   借据键已存在 → 2（重投重放，跳过）
     *   限购任一层超限 → 0（整体拒绝，什么都不动）
     *   库存不足 → -1（整体拒绝，什么都不动）
     *   全过 → 限购按层增加、库存扣减、写借据（同一脚本完成；借据键存在即"已扣"）
     *
     * KEYS: 1=活动Hash(集合)  2=活动计数  3=SKU quota  4=SKU计数  5=库存  6=借据
     * ARGV: 1=activityNo(取活动 Hash field)  2=数量  3=计数TTL  4=借据TTL
     * 活动上限经 cjson 解析 Hash field 的 purchaseLimit（null/缺失/解析失败按不限购宽容）
     */
    private static final String DEDUCT_LUA =
            "if redis.call('exists', KEYS[6]) == 1 then return 2 end " +
            "local quantity = tonumber(ARGV[2]) " +
            "local activityLimit = 0 " +
            "local activityJson = redis.call('hget', KEYS[1], ARGV[1]) " +
            "if activityJson then " +
            "  local ok, activity = pcall(cjson.decode, activityJson) " +
            "  if ok and activity['purchaseLimit'] then activityLimit = tonumber(activity['purchaseLimit']) or 0 end " +
            "end " +
            "local skuLimit = tonumber(redis.call('get', KEYS[3])) or 0 " +
            "if activityLimit > 0 then " +
            "  local current = tonumber(redis.call('get', KEYS[2])) or 0 " +
            "  if current + quantity > activityLimit then return 0 end " +
            "end " +
            "if skuLimit > 0 then " +
            "  local current = tonumber(redis.call('get', KEYS[4])) or 0 " +
            "  if current + quantity > skuLimit then return 0 end " +
            "end " +
            "local stock = tonumber(redis.call('get', KEYS[5])) " +
            "if stock == nil or stock < quantity then return -1 end " +
            "if activityLimit > 0 then " +
            "  redis.call('incrby', KEYS[2], quantity) " +
            "  redis.call('expire', KEYS[2], tonumber(ARGV[3])) " +
            "end " +
            "if skuLimit > 0 then " +
            "  redis.call('incrby', KEYS[4], quantity) " +
            "  redis.call('expire', KEYS[4], tonumber(ARGV[3])) " +
            "end " +
            "redis.call('decrby', KEYS[5], quantity) " +
            "redis.call('set', KEYS[6], ARGV[2], 'EX', tonumber(ARGV[4])) " +
            "return 1";

    /**
     * 三层一口原子回补（借据销账，各层独立判定；重复执行无副作用）：
     *   借据键不存在 → -1（未扣过或已回补，整包跳过）
     *   活动/SKU 层：计数键存在 → 本次借过该层 → 减回（下界 0、刷新 TTL）；键不在 → 跳过该层
     *   库存层：借据在 = 本次必扣过 → 直接加回；最后删借据键（原子销账）
     * 前提（不变量）：限购配置不可变——不限购层永不产生计数键，故"计数键存在"等价"本次借过该层"
     *
     * KEYS: 1=活动计数  2=SKU计数  3=库存  4=借据
     * ARGV: 1=数量  2=限购计数TTL
     * 注意：Redis Lua 中 GET 不存在的键返回 false，tonumber(false) 得到 nil（nil 参与算术会异常），
     * 故以 `or 0` 兜底为 0。
     */
    private static final String RESTORE_LUA =
            "if redis.call('exists', KEYS[4]) == 0 then return -1 end " +
            "local quantity = tonumber(ARGV[1]) " +
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "  local current = tonumber(redis.call('get', KEYS[1])) or 0 " +
            "  local target = current - quantity " +
            "  if target < 0 then target = 0 end " +
            "  redis.call('set', KEYS[1], target, 'EX', tonumber(ARGV[2])) " +
            "end " +
            "if redis.call('exists', KEYS[2]) == 1 then " +
            "  local current = tonumber(redis.call('get', KEYS[2])) or 0 " +
            "  local target = current - quantity " +
            "  if target < 0 then target = 0 end " +
            "  redis.call('set', KEYS[2], target, 'EX', tonumber(ARGV[2])) " +
            "end " +
            "redis.call('incrby', KEYS[3], quantity) " +
            "redis.call('del', KEYS[4]) " +
            "return 1";
}
