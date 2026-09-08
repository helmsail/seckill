package com.helmsail.seckill.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪过滤器
 *
 * Gateway 作为链路起点，始终生成新的 traceId，
 * 清理客户端可能伪造的 traceId，确保下游收到的是可信的值。
 */
@Component
public class TracingGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER_TRACE_ID = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 清理客户端 traceId，始终由 Gateway 生成
        final String traceId = UUID.randomUUID().toString().replace("-", "");

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HEADER_TRACE_ID);
                    headers.add(HEADER_TRACE_ID, traceId);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
