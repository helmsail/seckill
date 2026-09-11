package com.helmsail.seckill.common.tracing.dubbo;

import com.helmsail.seckill.common.tracing.BaggageKeys;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.MDC;

/**
 * Dubbo Provider 链路追踪过滤器
 *
 * 从上游 Dubbo Attachment 中读取 traceId 和 userId，写入 MDC 用于日志输出。
 */
@Activate(group = CommonConstants.PROVIDER)
public class BaggageProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String traceId = invocation.getAttachment(BaggageKeys.TRACE_ID);
        String userId = invocation.getAttachment(BaggageKeys.USER_ID);

        if (traceId != null) {
            MDC.put(BaggageKeys.TRACE_ID, traceId);
        }
        if (userId != null) {
            MDC.put(BaggageKeys.USER_ID, userId);
        }

        try {
            return invoker.invoke(invocation);
        } finally {
            MDC.remove(BaggageKeys.TRACE_ID);
            MDC.remove(BaggageKeys.USER_ID);
        }
    }
}
