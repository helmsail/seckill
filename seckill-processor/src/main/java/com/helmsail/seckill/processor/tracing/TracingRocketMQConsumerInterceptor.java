package com.helmsail.seckill.processor.tracing;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

/**
 * RocketMQ 消费者拦截器
 *
 * 在消费消息时，从消息 Header 中读取 userId、traceId 写入 MDC。
 */
@Slf4j
public abstract class TracingRocketMQConsumerInterceptor {

    private static final String USER_ID_KEY = "userId";
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 消费消息前，从 Header 中恢复链路信息到 MDC
     */
    protected void restoreTracing(Message<?> message) {
        MessageHeaders headers = message.getHeaders();

        String traceId = headers.get(TRACE_ID_KEY, String.class);
        String userId = headers.get(USER_ID_KEY, String.class);

        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
            UserContext.setUserId(userId);
        }
    }

    /**
     * 消费消息后，清理 MDC
     */
    protected void clearTracing() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(USER_ID_KEY);
        UserContext.clear();
    }
}
