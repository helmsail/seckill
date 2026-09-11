package com.helmsail.seckill.common.tracing.mq;

import com.helmsail.seckill.common.tracing.BaggageKeys;
import com.helmsail.seckill.common.tracing.UserContext;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;

/**
 * MQ 链路追踪工具
 *
 * 生产侧：从 MDC 提取链路信息写入消息 Header；
 * 消费侧：从消息属性恢复链路信息到 MDC 与 UserContext。
 */
public final class BaggageUtils {

    private BaggageUtils() {}

    /**
     * 构建携带链路信息的消息
     */
    public static <T> Message<T> buildMessage(T payload) {
        MessageBuilder<T> builder = MessageBuilder.withPayload(payload);
        String traceId = MDC.get(BaggageKeys.TRACE_ID);
        String userId = MDC.get(BaggageKeys.USER_ID);
        if (traceId != null) {
            builder.setHeader(BaggageKeys.TRACE_ID, traceId);
        }
        if (userId != null) {
            builder.setHeader(BaggageKeys.USER_ID, userId);
        }
        return builder.build();
    }

    /**
     * 从消息属性恢复链路信息
     */
    public static void restore(Map<String, String> properties) {
        if (properties == null) {
            return;
        }
        String traceId = properties.get(BaggageKeys.TRACE_ID);
        String userId = properties.get(BaggageKeys.USER_ID);
        if (traceId != null) {
            MDC.put(BaggageKeys.TRACE_ID, traceId);
        }
        if (userId != null) {
            MDC.put(BaggageKeys.USER_ID, userId);
            UserContext.setUserId(userId);
        }
    }

    /**
     * 清理链路信息
     */
    public static void clear() {
        MDC.remove(BaggageKeys.TRACE_ID);
        MDC.remove(BaggageKeys.USER_ID);
        UserContext.clear();
    }
}
