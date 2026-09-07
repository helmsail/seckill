package com.helmsail.seckill.service.tracing;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 生产者拦截器
 *
 * 在发送消息时，自动将 userId、traceId 写入消息 Header。
 */
@Slf4j
@Component
public class TracingRocketMQProducerInterceptor {

    private static final String USER_ID_KEY = "userId";
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 发送消息时自动携带链路信息
     */
    public void sendMessage(RocketMQTemplate rocketMQTemplate, String destination, Object payload) {
        Message<Object> message = org.springframework.messaging.support.MessageBuilder
                .withPayload(payload)
                .setHeader(TRACE_ID_KEY, MDC.get(TRACE_ID_KEY))
                .setHeader(USER_ID_KEY, UserContext.currentUserId())
                .build();

        rocketMQTemplate.send(destination, message);
    }
}
