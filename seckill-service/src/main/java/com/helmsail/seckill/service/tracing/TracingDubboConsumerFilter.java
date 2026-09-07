package com.helmsail.seckill.service.tracing;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.MDC;

/**
 * Dubbo 消费者链路追踪过滤器
 *
 * 从 MDC 中读取 userId、traceId，写入 Dubbo Attachment 传递给下游服务。
 */
@Slf4j
@Activate(group = CommonConstants.CONSUMER)
public class TracingDubboConsumerFilter implements Filter {

    private static final String USER_ID_KEY = "userId";
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 从 MDC 读取（HTTP Filter 已写入）
        String userId = MDC.get(USER_ID_KEY);
        String traceId = MDC.get(TRACE_ID_KEY);

        // 写入 Dubbo Attachment
        if (userId != null && !userId.isEmpty()) {
            RpcContext.getClientAttachment().setAttachment(USER_ID_KEY, userId);
        }
        if (traceId != null && !traceId.isEmpty()) {
            RpcContext.getClientAttachment().setAttachment(TRACE_ID_KEY, traceId);
        }

        try {
            return invoker.invoke(invocation);
        } finally {
            RpcContext.getClientAttachment().clearAttachments();
        }
    }
}
