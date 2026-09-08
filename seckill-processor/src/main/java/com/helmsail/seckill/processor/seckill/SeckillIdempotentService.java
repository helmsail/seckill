package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.redis.SeckillKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillIdempotentService {

    private static final String PENDING = "pending";
    private static final String PROCESSING = "processing";

    private final RedisService redisService;

    public boolean tryProcess(String traceId) {
        String key = String.format(SeckillKey.KEY_SECKILL_RESULT, traceId);
        String current = redisService.get(key);

        if (current == null) {
            log.warn("秒杀结果已过期: traceId={}", traceId);
            return false;
        }

        if (!PENDING.equals(current)) {
            log.info("秒杀已处理过: traceId={}, status={}", traceId, current);
            return false;
        }

        redisService.set(key, PROCESSING);
        log.info("秒杀开始处理: traceId={}", traceId);
        return true;
    }

    public void markSuccess(String traceId, String orderNo) {
        String key = String.format(SeckillKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, orderNo);
        log.info("秒杀处理成功: traceId={}, orderNo={}", traceId, orderNo);
    }

    public void markFailed(String traceId, String reason) {
        String key = String.format(SeckillKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, "failed:" + reason);
        log.info("秒杀处理失败: traceId={}, reason={}", traceId, reason);
    }
}
