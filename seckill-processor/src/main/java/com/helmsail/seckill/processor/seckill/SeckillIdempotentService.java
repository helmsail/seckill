package com.helmsail.seckill.processor.seckill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.seckill.SeckillResultStatus;
import com.helmsail.seckill.base.seckill.SeckillResultVO;
import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀幂等与结果回写（定位键：traceId，与请求一一对应）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillIdempotentService {

    /** 处理中占位有效期（秒）：进程崩溃残留自动过期，避免键永久占用 */
    private static final int PROCESSING_TTL_SECONDS = 30 * 60;

    /** 终态结果保留时长（秒）：供前端轮询获取订单号/失败原因 */
    private static final int FINAL_TTL_SECONDS = 5 * 60;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * 尝试处理（原子操作，基于 SETNX）
     *
     * @return true 表示可以处理，false 表示已处理过（终态或处理中）
     */
    public boolean tryProcess(String traceId) {
        String key = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        Boolean success = redisService.setIfAbsent(key,
                write(new SeckillResultVO(SeckillResultStatus.PROCESSING, null, null)),
                PROCESSING_TTL_SECONDS, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(success)) {
            log.info("秒杀开始处理: traceId={}", traceId);
        }
        return Boolean.TRUE.equals(success);
    }

    public void markSuccess(String traceId, String orderNo) {
        String key = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, write(new SeckillResultVO(SeckillResultStatus.SUCCESS, orderNo, null)),
                FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("秒杀处理成功: traceId={}, orderNo={}", traceId, orderNo);
    }

    public void markFailed(String traceId, String reason) {
        String key = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, write(new SeckillResultVO(SeckillResultStatus.FAILED, null, reason)),
                FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("秒杀处理失败: traceId={}, reason={}", traceId, reason);
    }

    private String write(SeckillResultVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化秒杀结果失败", e);
        }
    }
}
