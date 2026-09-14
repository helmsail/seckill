package com.helmsail.seckill.service.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.base.seckill.SeckillResultVO;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.common.tracing.BaggageKeys;
import com.helmsail.seckill.common.tracing.UserContext;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

/**
 * 秒杀编排服务
 *
 * 准入检查全部委托 CheckService（逐项失败即抛对应业务码），
 * 通过后发送 MQ 异步下单；结果由 processor 写回结果键，C 端轮询获取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final CheckService checkService;
    private final RedisService redisService;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public String executeSeckill(SeckillRequest request) {
        String userId = UserContext.currentUserId();
        String activityNo = request.getActivityNo();
        String skuNo = request.getSkuNo();
        String traceId = MDC.get(BaggageKeys.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            // traceId 由网关生成并透传，缺失即链路断裂：快速失败暴露，不做静默兜底
            // （traceId 兼任幂等键与结果键，兜底会掩盖透传故障并割裂全链路日志）
            throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(), "请求缺失链路标识 traceId");
        }

        // 六项准入检查按序收缩，任一失败即在 CheckService 内抛对应业务码
        checkService.checkRateLimit(userId);
        checkService.checkActivity(activityNo, userId);
        checkService.checkBlacklist(userId);
        checkService.checkPurchaseLimit(activityNo, skuNo, userId, request.getQuantity());
        checkService.checkSkuOnShelf(activityNo, skuNo);
        checkService.checkStock(activityNo, skuNo, request.getQuantity());

        request.setUserId(userId);
        request.setTraceId(traceId);

        // 同步发送（send 即同步）。发送异常/超时只能说明"结果不确定"：客户端已内置重试（默认 2 次），
        // 消息可能已实际到达 broker；此时向用户返回失败会诱导重试——重试请求携带新 traceId，
        // 绕过消费端按 traceId 的幂等链，造成真正重复的下单尝试。
        // 故发送异常只记日志、仍返回 traceId：消息到达则结果轮询必有所获，同 traceId 的重复投递由幂等链去重；
        // 消息彻底丢失的极端情况由用户侧轮询超时兜底。
        // SendStatus 不做显式判定：本集群 ASYNC_FLUSH + 单 master，FLUSH_DISK_TIMEOUT/SLAVE_* 实际不可达，
        // 且消息只要入 broker 存储即会投递，判定结果已无动作可做，回归默认行为。
        try {
            String json = objectMapper.writeValueAsString(request);
            Message<String> message = BaggageUtils.buildMessage(json);
            rocketMQTemplate.send(MqTopic.SECKILL_ORDER, message);
        } catch (Exception e) {
            log.error("秒杀消息发送结果不确定（消息可能已投递，以结果轮询为准）: traceId={}", traceId, e);
        }

        log.info("秒杀请求已提交: userId={}, activityNo={}, skuNo={}, traceId={}", userId, activityNo, skuNo, traceId);
        return traceId;
    }

    public SeckillResultVO pollResult(String traceId) {
        String resultKey = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        String json = redisService.get(resultKey);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SeckillResultVO.class);
        } catch (Exception e) {
            log.error("秒杀结果解析失败: traceId={}", traceId, e);
            return null;
        }
    }
}
