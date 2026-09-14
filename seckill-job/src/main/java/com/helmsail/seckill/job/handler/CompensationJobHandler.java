package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.compensation.CompensationType;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.support.api.sku.SkuDubboService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 库存归还补偿任务（兜底各类"归还失败"的遗留）
 *
 * 数据来源：seckill:compensation:pending（Hash，value=JSON 明细），按 type 分发：
 *   - 默认（无 type / legacy）：admin 编排中"归还主域库存失败"（ADD_ROLLBACK / REMOVE_RESTORE，
 *     动作统一为归还主域库存，requestId 经服务端幂等去重）；
 *   - SECKILL_STOCK：关单/失败回滚时秒杀域库存回补失败（Redis 计数加回，标记守卫幂等）；
 *   - SECKILL_LIMIT：秒杀域限购额度恢复失败（Redis 计数减回，标记守卫幂等）。
 * 上限 MAX_RETRY，超限转 seckill:compensation:failed 留档并告警（人工处理）。
 *
 * 建议 xxl-job 调度：每分钟一次（补偿时效要求高于订单超时扫描）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationJobHandler {

    /** 单条补偿最大重试次数，超限转人工 */
    private static final int MAX_RETRY = 3;

    /** 限购计数键保留时长（秒）：与 processor 侧 PurchaseLimitService 跨模块同构保持一致 */
    private static final int LIMIT_COUNTER_TTL_SECONDS = 30 * 24 * 60 * 60;

    @DubboReference
    private SkuDubboService skuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @XxlJob("compensationJob")
    @SuppressWarnings("unchecked")
    public void execute() {
        Map<Object, Object> pending = redisService.hGetAll(SeckillRedisKey.KEY_COMPENSATION_PENDING);
        if (pending.isEmpty()) {
            return;
        }
        int done = 0;
        for (Map.Entry<Object, Object> entry : pending.entrySet()) {
            String field = (String) entry.getKey();
            String value = (String) entry.getValue();
            try {
                Map<String, Object> record = objectMapper.readValue(value, Map.class);
                dispatch(field, record);
                redisService.hDel(SeckillRedisKey.KEY_COMPENSATION_PENDING, field);
                done++;
            } catch (Exception e) {
                retryOrTransfer(field, value, e);
            }
        }
        log.info("库存补偿任务完成: 扫描={}, 成功归还={}", pending.size(), done);
    }

    /** 按类型分发执行（新增类型在此扩展分支部） */
    private void dispatch(String field, Map<String, Object> record) {
        Object type = record.get("type");
        if (CompensationType.SECKILL_STOCK.equals(type)) {
            restoreSeckillStock(record);
            return;
        }
        if (CompensationType.SECKILL_LIMIT.equals(type)) {
            restoreSeckillLimit(record);
            return;
        }
        // 默认（legacy）：admin 侧主域库存归还
        restoreMainDomainStock(field, record);
    }

    /** 主域库存归还（原路径）：requestId 幂等由服务端去重；历史无 ID 记录按 field 降级派生（跨轮稳定） */
    private void restoreMainDomainStock(String field, Map<String, Object> record) {
        String skuNo = String.valueOf(record.get("skuNo"));
        int stock = ((Number) record.get("stock")).intValue();
        String requestId = record.get("requestId") instanceof String id && !id.isBlank()
                ? id : "compensation:" + field;
        skuService.addStock(skuNo, stock, requestId);
    }

    /** 秒杀域库存回补（标记守卫：标记存在才加回并删标记，重复执行无副作用） */
    private void restoreSeckillStock(Map<String, Object> record) {
        String activityNo = String.valueOf(record.get("activityNo"));
        String skuNo = String.valueOf(record.get("skuNo"));
        int quantity = ((Number) record.get("quantity")).intValue();
        String traceId = String.valueOf(record.get("traceId"));
        String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo);
        String markKey = String.format(SeckillRedisKey.KEY_DEDUCT_STOCK, traceId);
        redisService.executeLua(RESTORE_STOCK_LUA, List.of(stockKey, markKey), String.valueOf(quantity));
    }

    /** 秒杀域限购恢复（标记守卫：标记存在才减回并删标记，重复执行无副作用） */
    private void restoreSeckillLimit(Map<String, Object> record) {
        String activityNo = String.valueOf(record.get("activityNo"));
        String skuNo = String.valueOf(record.get("skuNo"));
        String userId = String.valueOf(record.get("userId"));
        int quantity = ((Number) record.get("quantity")).intValue();
        String traceId = String.valueOf(record.get("traceId"));
        String counterKey = String.format(SeckillRedisKey.KEY_PURCHASE_LIMIT, activityNo, skuNo, userId);
        String markKey = String.format(SeckillRedisKey.KEY_DEDUCT_LIMIT, traceId);
        redisService.executeLua(RESTORE_LIMIT_LUA, List.of(counterKey, markKey),
                String.valueOf(quantity), String.valueOf(LIMIT_COUNTER_TTL_SECONDS));
    }

    /** 重试计数 +1 回写；超限（或记录无法解析）转入 failed Hash 留档告警 */
    @SuppressWarnings("unchecked")
    private void retryOrTransfer(String field, String value, Exception cause) {
        Map<String, Object> record;
        int retryCount;
        try {
            record = objectMapper.readValue(value, Map.class);
            retryCount = record.get("retryCount") instanceof Number n ? n.intValue() : 0;
        } catch (Exception parseEx) {
            redisService.hSet(SeckillRedisKey.KEY_COMPENSATION_FAILED, field, value);
            redisService.hDel(SeckillRedisKey.KEY_COMPENSATION_PENDING, field);
            log.error("补偿记录无法解析，转人工处理: field={}, value={}", field, value, parseEx);
            return;
        }
        if (retryCount + 1 > MAX_RETRY) {
            redisService.hSet(SeckillRedisKey.KEY_COMPENSATION_FAILED, field, value);
            redisService.hDel(SeckillRedisKey.KEY_COMPENSATION_PENDING, field);
            log.error("补偿重试超限，转人工处理: field={}, 已重试={}次, error={}", field, retryCount, cause.getMessage());
            return;
        }
        record.put("retryCount", retryCount + 1);
        record.put("lastError", cause.getClass().getSimpleName() + ": " + cause.getMessage());
        try {
            redisService.hSet(SeckillRedisKey.KEY_COMPENSATION_PENDING, field,
                    objectMapper.writeValueAsString(record));
            log.warn("补偿归还失败待重试({}/{}): field={}, error={}", retryCount + 1, MAX_RETRY, field, cause.getMessage());
        } catch (Exception serEx) {
            log.error("补偿记录回写失败: field={}", field, serEx);
        }
    }

    /** 库存回补脚本：与 processor 侧 StockService.RESTORE_LUA 同构（跨模块各自持有，改动须同步） */
    private static final String RESTORE_STOCK_LUA =
            "if redis.call('exists', KEYS[2]) == 0 then return -1 end " +
            "redis.call('incrby', KEYS[1], tonumber(ARGV[1])) " +
            "redis.call('del', KEYS[2]) " +
            "return 1";

    /** 限购恢复脚本：与 processor 侧 PurchaseLimitService.RESTORE_LUA 同构（跨模块各自持有，改动须同步） */
    private static final String RESTORE_LIMIT_LUA =
            "if redis.call('exists', KEYS[2]) == 0 then return -1 end " +
            "local current = tonumber(redis.call('get', KEYS[1])) or 0 " +
            "local target = current - tonumber(ARGV[1]) " +
            "if target < 0 then target = 0 end " +
            "redis.call('set', KEYS[1], target, 'EX', tonumber(ARGV[2])) " +
            "redis.call('del', KEYS[2]) " +
            "return 1";
}
