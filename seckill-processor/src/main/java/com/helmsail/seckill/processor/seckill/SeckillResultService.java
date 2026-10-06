package com.helmsail.seckill.processor.seckill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderDubboService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.base.seckill.SeckillResultStatus;
import com.helmsail.seckill.base.seckill.SeckillResultVO;
import com.helmsail.seckill.common.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀消费结果服务（定位键：traceId，与请求一一对应）
 *
 * 消费结果的记录与准入裁定——重放正确性由各环节自身的幂等保证（扣减标记/唯一键/订单表），
 * 本类提供结果状态机与基于状态的准入决策：
 * 结果状态机（markProcessing → markSuccess / markFailed）——"订单表最高权威 + PROCESSING 占位"：
 *   ① 任何状态下已建单 → 补写成功终态并短路（DB 持久化，不惧 Redis 丢失/过期）；
 *   ② 无单走 SETNX 占位：抢到放行；未抢到读结果键——终态屏蔽；处理中判定崩溃残留、续期后接管重放。
 * 结果键：PROCESSING 记录处理中（兼作接管信号）；SUCCESS/FAILED 为终态，供轮询与重投屏蔽。
 * 仅提供结果状态能力，不含资源回补——异常终局由 consumer 编排（本类保持对其他业务服务的零依赖）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillResultService {

    /** 处理中占位有效期（秒）：进程崩溃残留自动过期，避免键永久占用 */
    private static final int PROCESSING_TTL_SECONDS = 30 * 60;

    /**
     * 终态结果保留时长（秒）：兼作防重放窗口——覆盖 MQ 重投周期（最长小时级），
     * 期间任何重投都被闸门直接拦截；同时供前端事后查询订单号/失败原因
     */
    private static final int FINAL_TTL_SECONDS = 24 * 60 * 60;

    @DubboReference
    private SeckillOrderDubboService seckillOrderService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * 标记结果处理中（重投安全核心）：订单裁定 → 占位抢注 → 终态屏蔽/接管续期，一口完成
     *
     * ① 订单表最高权威：已建单即补写成功终态并短路（结果键过期/Redis 丢失等极端重放的兜底）；
     * ② SETNX 抢到占位 → 标记成功（首次执行快路径）；
     * ③ 未抢到 → 读结果键：终态屏蔽（重投去重）；处理中（或读取为 null 的过期窗口）判定
     *    崩溃残留，续期占位后接管重放（重放安全由幂等标记与唯一约束保证）。
     *
     * @return true 表示已标记处理中、可继续处理
     */
    public boolean markProcessing(SeckillRequest request) {
        String traceId = request.getTraceId();
        // 订单表最高权威：已建单 → 补写成功终态并短路（结果键过期/Redis 丢失等极端重放的兜底）
        SeckillOrderDTO existing = seckillOrderService.getByTraceId(
                Long.parseLong(request.getUserId()), request.getTraceId());
        if (existing != null) {
            log.warn("订单已存在，补写成功终态: traceId={}, orderNo={}", traceId, existing.getOrderNo());
            markSuccess(traceId, existing.getOrderNo());
            return false;
        }
        String key = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        String processing = toJson(new SeckillResultVO(SeckillResultStatus.PROCESSING, null, null));
        // 占位抢注（SETNX 原子）：抢到即首次标记
        if (Boolean.TRUE.equals(redisService.setIfAbsent(key, processing,
                PROCESSING_TTL_SECONDS, TimeUnit.SECONDS))) {
            log.info("秒杀开始处理: traceId={}", traceId);
            return true;
        }
        // 未抢到：读当前结果——终态屏蔽；处理中判定崩溃残留，续期后接管
        SeckillResultVO current = null;
        String json = redisService.get(key);
        if (json != null) {
            try {
                current = objectMapper.readValue(json, SeckillResultVO.class);
            } catch (Exception e) {
                log.warn("秒杀结果解析失败，按未处理对待: traceId={}", traceId, e);
            }
        }
        if (current != null && !SeckillResultStatus.PROCESSING.equals(current.getStatus())) {
            log.info("秒杀结果已终结，重投屏蔽: traceId={}, status={}", traceId, current.getStatus());
            return false;
        }
        redisService.set(key, processing, PROCESSING_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("处理占位续期（接管重放）: traceId={}", traceId);
        return true;
    }

    public void markSuccess(String traceId, String orderNo) {
        String key = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, toJson(new SeckillResultVO(SeckillResultStatus.SUCCESS, orderNo, null)),
                FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("秒杀处理成功: traceId={}, orderNo={}", traceId, orderNo);
    }

    public void markFailed(String traceId, String reason) {
        String key = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(key, toJson(new SeckillResultVO(SeckillResultStatus.FAILED, null, reason)),
                FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("秒杀处理失败: traceId={}, reason={}", traceId, reason);
    }

    /** 结果 VO → JSON（序列化失败属编程错误，直接上抛） */
    private String toJson(SeckillResultVO vo) {
        try {
            return objectMapper.writeValueAsString(vo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化秒杀结果失败", e);
        }
    }
}
