package com.helmsail.seckill.common.tracing.http;

import com.helmsail.seckill.common.tracing.BaggageKeys;
import com.helmsail.seckill.common.tracing.UserContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * HTTP 入口链路追踪过滤器
 *
 * 从 HTTP Header 中读取 userId 和 traceId，
 * 写入 MDC 与 UserContext，finally 中清理。
 */
public class HttpBaggageFilter implements Filter, Ordered {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String userId = httpRequest.getHeader(BaggageKeys.USER_ID);
        String traceId = httpRequest.getHeader(BaggageKeys.TRACE_ID);

        if (userId != null) {
            MDC.put(BaggageKeys.USER_ID, userId);
            UserContext.setUserId(userId);
        }
        if (traceId != null) {
            MDC.put(BaggageKeys.TRACE_ID, traceId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(BaggageKeys.USER_ID);
            MDC.remove(BaggageKeys.TRACE_ID);
            UserContext.clear();
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
