package com.helmsail.seckill.job.tracing;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.slf4j.MDC;

/**
 * 任务调度链路追踪过滤器
 *
 * 从 MDC 中读取 traceId（由 JobHandler 任务启动时生成），
 * 写入 Dubbo Attachment 传递给下游服务。
 */
@Slf4j
@Activate(group = CommonConstants.CONSUMER)
public class JobBaggageConsumerFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 从 MDC 读取 traceId（任务启动时已生成）
        String traceId = MDC.get(TRACE_ID_KEY);

        // 写入 Dubbo Attachment（传递给下游服务）
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
