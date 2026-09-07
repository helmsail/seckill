package com.helmsail.seckill.service.tracing;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.io.IOException;

/**
 * 链路追踪过滤器
 *
 * 从 HTTP Header 中读取 userId 和 traceId，
 * 写入 MDC 用于本地日志，写入 Dubbo Attachment 传递给下游。
 */
@Slf4j
@Component
@Order(1)
public class TracingFilter implements Filter {

    private static final String USER_ID_KEY = "userId";
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String userId = httpRequest.getHeader(USER_ID_KEY);
        String traceId = httpRequest.getHeader(TRACE_ID_KEY);

        // 写入 MDC（用于本地日志）
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
            UserContext.setUserId(userId);
        }
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }

        // 写入 Dubbo Attachment（传递给下游服务）
        if (userId != null) {
            RpcContext.getClientAttachment().setAttachment(USER_ID_KEY, userId);
        }
        if (traceId != null) {
            RpcContext.getClientAttachment().setAttachment(TRACE_ID_KEY, traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_KEY);
            MDC.remove(TRACE_ID_KEY);
            UserContext.clear();
            RpcContext.getClientAttachment().clearAttachments();
        }
    }
}
