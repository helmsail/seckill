package com.helmsail.seckill.common.tracing.dubbo;

import com.helmsail.seckill.common.tracing.BaggageKeys;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.MDC;

/**
 * Dubbo Consumer 链路追踪过滤器
 *
 * 从 MDC 中读取 traceId 和 userId，写入 Dubbo Attachment 传递给下游服务。
 */
@Activate(group = CommonConstants.CONSUMER)
public class BaggageConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String traceId = MDC.get(BaggageKeys.TRACE_ID);
        String userId = MDC.get(BaggageKeys.USER_ID);

        if (traceId != null && !traceId.isEmpty()) {
            RpcContext.getClientAttachment().setAttachment(BaggageKeys.TRACE_ID, traceId);
        }
        if (userId != null && !userId.isEmpty()) {
            RpcContext.getClientAttachment().setAttachment(BaggageKeys.USER_ID, userId);
        }

        try {
            return invoker.invoke(invocation);
        } finally {
            RpcContext.getClientAttachment().clearAttachments();
        }
    }
}
