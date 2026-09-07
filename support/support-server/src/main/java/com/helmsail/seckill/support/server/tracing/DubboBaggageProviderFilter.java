package com.helmsail.seckill.support.server.tracing;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.MDC;

/**
 * Dubbo Provider 链路追踪过滤器
 *
 * 从上游 Dubbo Attachment 中读取 traceId 和 userId，
 * 写入 MDC 和 UserContext，finally 中清理。
 */
@Activate(group = CommonConstants.PROVIDER)
public class DubboBaggageProviderFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String USER_ID_KEY = "userId";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String traceId = invocation.getAttachment(TRACE_ID_KEY);
        String userId = invocation.getAttachment(USER_ID_KEY);

        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
            UserContext.setUserId(userId);
        }

        try {
            return invoker.invoke(invocation);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(USER_ID_KEY);
            UserContext.clear();
        }
    }
}
