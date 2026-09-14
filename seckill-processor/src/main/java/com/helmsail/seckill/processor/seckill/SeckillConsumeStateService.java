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
 * 秒杀消费状态管理（定位键：traceId，与请求一一对应）
 *
 * PROCESSING 占位兼作幂等闸门（并发副本互斥）；SUCCESS/FAILED 为终态，供轮询与重投屏蔽。
 * 闸门语义：终态由调用方直接屏蔽；PROCESSING 占位不代表已完成——由调用方查订单表裁定
 * （有单补写终态，无单属崩溃残留、续期占位后接管重放）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillConsumeStateService {

    /** 处理中占位有效期（秒）：进程崩溃残留自动过期，避免键永久占用 */
    private static final int PROCESSING_TTL_SECONDS = 30 * 60;

    /**
     * 终态结果保留时长（秒）：兼作防重放窗口——覆盖 MQ 重投周期（最长小时级），
     * 期间任何重投都被 tryProcess 直接拦截；同时供前端事后查询订单号/失败原因
     */
    private static final int FINAL_TTL_SECONDS = 24 * 60 * 60;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * 尝试处理（原子操作，基于 SETNX）
     *
     * @return true 表示抢到占位可处理；false 表示闸门已被占用（终态或处理中占位，由调用方按结果键/订单表裁定）
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

    /**
     * 读取当前结果（键不存在返回 null；解析失败按未处理对待并告警）
     */
    public SeckillResultVO getResult(String traceId) {
        String key = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        String json = redisService.get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SeckillResultVO.class);
        } catch (Exception e) {
            log.warn("秒杀结果解析失败，按未处理对待: traceId={}", traceId, e);
            return null;
        }
    }

    /**
     * 占位续期（崩溃残留接管重放前调用）：覆盖写 PROCESSING 并刷新 TTL
     */
    public void renewProcessing(String traceId) {
        String key = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, write(new SeckillResultVO(SeckillResultStatus.PROCESSING, null, null)),
                PROCESSING_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("处理占位续期（接管重放）: traceId={}", traceId);
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
