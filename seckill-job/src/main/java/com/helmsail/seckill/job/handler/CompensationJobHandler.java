package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.support.api.sku.SkuService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 库存归还补偿任务（兜底 admin 编排中"归还主域库存失败"的遗留）
 *
 * 数据来源：seckill:compensation:pending（admin 侧登记，含 ADD_ROLLBACK / REMOVE_RESTORE
 * 两类，动作统一为“归还主域库存”）。记录携带 requestId，重试经服务端幂等去重
 * （重复执行无副作用）；上限 MAX_RETRY，超限转 seckill:compensation:failed 留档并告警（人工处理）。
 *
 * 建议 xxl-job 调度：每分钟一次（补偿时效要求高于订单超时扫描）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationJobHandler {

    /** 单条补偿最大重试次数，超限转人工 */
    private static final int MAX_RETRY = 3;

    @DubboReference
    private SkuService skuService;

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
                String skuNo = String.valueOf(record.get("skuNo"));
                int stock = ((Number) record.get("stock")).intValue();
                // requestId 幂等：重试重复执行无副作用；历史无 ID 记录按 field 降级派生（跨轮稳定）
                String requestId = record.get("requestId") instanceof String id && !id.isBlank()
                        ? id : "compensation:" + field;
                skuService.addStock(skuNo, stock, requestId);
                redisService.hDel(SeckillRedisKey.KEY_COMPENSATION_PENDING, field);
                done++;
            } catch (Exception e) {
                retryOrTransfer(field, value, e);
            }
        }
        log.info("库存补偿任务完成: 扫描={}, 成功归还={}", pending.size(), done);
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
}
