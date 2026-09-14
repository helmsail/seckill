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

        // 结果键由消费端（processor）统一创建与回写；发送失败直接抛错，用户无需轮询
        sendMqMessage(request);

        log.info("秒杀请求已提交: userId={}, activityNo={}, skuNo={}, traceId={}", userId, activityNo, skuNo, traceId);
        return traceId;
    }

    private void sendMqMessage(SeckillRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            Message<String> message = BaggageUtils.buildMessage(json);
            rocketMQTemplate.send(MqTopic.SECKILL_ORDER, message);
        } catch (Exception e) {
            throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(), "发送消息失败");
        }
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
