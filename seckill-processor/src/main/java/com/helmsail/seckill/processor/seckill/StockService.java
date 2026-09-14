package com.helmsail.seckill.processor.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.compensation.CompensationType;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀库存扣减（Redis 原子操作，运行期唯一权威）
 *
 * key 由 activityNo + skuNo 定位；库存计数仅由预热窗口缺省初始化、此处扣/补，缓存同步任务其余分支不触碰。
 * 扣减与 traceId 幂等标记同脚本原子写入：MQ 重投重放时跳过重复扣减（返回"已扣"视为成功）；
 * 回补为标记守卫的原子操作（标记存在才加回并删标记），回补失败登记持久化补偿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    /** 扣减标记有效期（秒）：与结果键防重放窗口（24h）齐平 */
    private static final int DEDUCT_MARK_TTL_SECONDS = 24 * 60 * 60;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * 扣减库存（原子操作，含 traceId 幂等标记）
     *
     * @return true 表示扣减成立（本次扣减成功或本请求此前已扣减）；false 表示库存不足
     */
    public boolean deduct(String activityNo, String skuNo, int quantity, String traceId) {
        String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo);
        String markKey = String.format(SeckillRedisKey.KEY_DEDUCT_STOCK, traceId);
        Long result = redisService.executeLua(DEDUCT_LUA, List.of(stockKey, markKey),
                String.valueOf(quantity), String.valueOf(DEDUCT_MARK_TTL_SECONDS));
        // 1=本次扣减成功；2=标记已存在（重投重放，视为已扣）；0=库存不足
        return result != null && result > 0;
    }

    /**
     * 回补库存（标记守卫原子操作：标记存在才加回并删除标记，重复执行无副作用）
     *
     * 不抛出异常：回补失败登记持久化补偿（compensationJob 重试），调用方按完成处理。
     */
    public void restore(String activityNo, String skuNo, int quantity, String traceId) {
        try {
            String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo);
            String markKey = String.format(SeckillRedisKey.KEY_DEDUCT_STOCK, traceId);
            redisService.executeLua(RESTORE_LUA, List.of(stockKey, markKey), String.valueOf(quantity));
        } catch (Exception e) {
            log.error("回补库存失败，登记持久化补偿: activityNo={}, skuNo={}, quantity={}, traceId={}",
                    activityNo, skuNo, quantity, traceId, e);
            registerCompensation(activityNo, skuNo, quantity, traceId);
        }
    }

    /** 登记库存回补补偿（field 含 traceId 保证逐请求唯一；登记失败属 Redis 整体不可用残余风险，日志留痕） */
    private void registerCompensation(String activityNo, String skuNo, int quantity, String traceId) {
        try {
            Map<String, Object> record = new HashMap<>();
            record.put("type", CompensationType.SECKILL_STOCK);
            record.put("activityNo", activityNo);
            record.put("skuNo", skuNo);
            record.put("quantity", quantity);
            record.put("traceId", traceId);
            record.put("retryCount", 0);
            String field = CompensationType.SECKILL_STOCK + ":" + activityNo + ":" + skuNo + ":" + traceId;
            redisService.hSet(SeckillRedisKey.KEY_COMPENSATION_PENDING, field,
                    objectMapper.writeValueAsString(record));
            log.warn("库存回补已登记持久化补偿: field={}", field);
        } catch (Exception e) {
            log.error("库存回补补偿登记失败（需人工核对）: activityNo={}, skuNo={}, traceId={}",
                    activityNo, skuNo, traceId, e);
        }
    }

    /** 扣减：标记已存在→返回 2（重放跳过）；库存不足→0；成功→扣减并写标记（value=数量，TTL 防键永驻） */
    private static final String DEDUCT_LUA =
            "if redis.call('exists', KEYS[2]) == 1 then return 2 end " +
            "local stock = tonumber(redis.call('get', KEYS[1])) " +
            "if stock == nil then return 0 end " +
            "local quantity = tonumber(ARGV[1]) " +
            "if stock < quantity then return 0 end " +
            "redis.call('decrby', KEYS[1], quantity) " +
            "redis.call('set', KEYS[2], ARGV[1], 'EX', tonumber(ARGV[2])) " +
            "return 1";

    /** 回补：标记不存在→返回 -1（未扣过或已回补，跳过）；加回库存并删标记（原子） */
    private static final String RESTORE_LUA =
            "if redis.call('exists', KEYS[2]) == 0 then return -1 end " +
            "redis.call('incrby', KEYS[1], tonumber(ARGV[1])) " +
            "redis.call('del', KEYS[2]) " +
            "return 1";
}
