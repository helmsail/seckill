package com.helmsail.seckill.service.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.base.seckill.SeckillResultVO;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.common.tracing.BaggageKeys;
import com.helmsail.seckill.common.tracing.UserContext;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import com.helmsail.seckill.service.activity.ActivityQueryService;
import com.helmsail.seckill.service.check.BlacklistCheckService;
import com.helmsail.seckill.service.check.RateLimitCheckService;
import com.helmsail.seckill.service.config.SeckillConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final RateLimitCheckService rateLimitCheckService;
    private final BlacklistCheckService blacklistCheckService;
    private final ActivityQueryService activityQueryService;
    private final RedisService redisService;
    private final SeckillConfig config;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public String executeSeckill(SeckillRequest request) {
        String userId = UserContext.currentUserId();
        String activityNo = request.getActivityNo();
        String skuNo = request.getSkuNo();
        String traceId = MDC.get(BaggageKeys.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            // 兜底：绕过网关直连时无 traceId，生成临时值保证结果键唯一
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        if (!rateLimitCheckService.check(userId)) {
            throw new BizException(SeckillResultEnum.RATE_LIMITED);
        }

        if (!blacklistCheckService.checkActivityStatus(activityNo, userId)) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR);
        }

        if (!activityQueryService.isInEffectiveWindow(activityNo)) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_EFFECTIVE);
        }

        if (!activityQueryService.isSkuOnShelf(activityNo, skuNo)) {
            throw new BizException(SeckillResultEnum.SKU_OFF_SHELF);
        }

        if (!blacklistCheckService.check(userId)) {
            throw new BizException(SeckillResultEnum.BLACKLISTED);
        }

        if (config.getCheck().isStock()) {
            Integer stock = activityQueryService.getSkuStock(activityNo, skuNo);
            if (stock == null || stock < request.getQuantity()) {
                throw new BizException(SeckillResultEnum.STOCK_INSUFFICIENT);
            }
        }

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
