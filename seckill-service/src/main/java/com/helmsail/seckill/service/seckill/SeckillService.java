package com.helmsail.seckill.service.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.mq.MqTopic;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.redis.SeckillResultStatus;
import com.helmsail.seckill.common.redis.SeckillServiceKey;
import com.helmsail.seckill.common.request.SeckillRequest;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.service.activity.ActivityQueryService;
import com.helmsail.seckill.service.check.BlacklistCheckService;
import com.helmsail.seckill.service.check.RateLimitCheckService;
import com.helmsail.seckill.service.config.SeckillConfig;
import com.helmsail.seckill.service.tracing.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private static final String TRACE_ID_KEY = "traceId";

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
        String traceId = MDC.get(TRACE_ID_KEY);

        if (!rateLimitCheckService.check(userId)) {
            throw new BizException(ResultEnum.RATE_LIMITED);
        }

        if (!blacklistCheckService.checkActivityStatus(activityNo, userId)) {
            throw new BizException(ResultEnum.ACTIVITY_STATUS_ERROR);
        }

        if (!blacklistCheckService.check(userId)) {
            throw new BizException(ResultEnum.BLACKLISTED);
        }

        if (config.getCheck().isStock()) {
            Integer stock = activityQueryService.getSkuStock(skuNo);
            if (stock == null || stock < request.getQuantity()) {
                throw new BizException(ResultEnum.STOCK_INSUFFICIENT);
            }
        }

        request.setUserId(userId);
        sendMqMessage(request);

        String resultKey = String.format(SeckillServiceKey.KEY_SECKILL_RESULT, traceId);
        redisService.set(resultKey, SeckillResultStatus.PENDING, config.getResult().getExpireSeconds(), TimeUnit.SECONDS);

        log.info("秒杀请求已提交: userId={}, activityNo={}, skuNo={}, traceId={}", userId, activityNo, skuNo, traceId);
        return traceId;
    }

    private void sendMqMessage(SeckillRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            Message<String> message = MessageBuilder.withPayload(json).build();
            rocketMQTemplate.send(MqTopic.SECKILL_ORDER, message);
        } catch (Exception e) {
            throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(), "发送消息失败");
        }
    }

    public String pollResult(String traceId) {
        String resultKey = String.format(SeckillServiceKey.KEY_SECKILL_RESULT, traceId);
        return redisService.get(resultKey);
    }
}
