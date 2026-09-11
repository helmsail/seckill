package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.base.redis.SeckillResultStatus;
import com.helmsail.seckill.base.redis.SeckillServiceKey;
import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillIdempotentService {

    private final RedisService redisService;

    /**
     * 尝试处理（原子操作，基于 SETNX）
     *
     * @return true 表示可以处理，false 表示已处理过
     */
    public boolean tryProcess(String traceId) {
        String key = String.format(SeckillServiceKey.KEY_SECKILL_RESULT, traceId);
        Boolean success = redisService.setIfAbsent(key, SeckillResultStatus.PROCESSING);
        if (Boolean.TRUE.equals(success)) {
            log.info("秒杀开始处理: traceId={}", traceId);
        }
        return Boolean.TRUE.equals(success);
    }

    public void markSuccess(String traceId, String orderNo) {
        String key = String.format(SeckillServiceKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, orderNo);
        log.info("秒杀处理成功: traceId={}, orderNo={}", traceId, orderNo);
    }

    public void markFailed(String traceId, String reason) {
        String key = String.format(SeckillServiceKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, SeckillResultStatus.FAILED + ":" + reason);
        log.info("秒杀处理失败: traceId={}, reason={}", traceId, reason);
    }
}
