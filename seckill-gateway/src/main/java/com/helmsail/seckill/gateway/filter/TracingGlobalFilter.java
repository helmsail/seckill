package com.helmsail.seckill.gateway.filter;

import com.helmsail.seckill.common.tracing.BaggageKeys;
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
 *
 * order 设为最高优先级：认证/授权/限流失败（401/403/429）的请求不进入业务链路，
 * 但这类安全事件的排查恰恰依赖 traceId，故追踪必须早于所有其他过滤器。
 */
@Component
public class TracingGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 清理客户端 traceId，始终由 Gateway 生成
        final String traceId = UUID.randomUUID().toString().replace("-", "");

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    // 键名以 common 的 BaggageKeys 为准（与后端 HttpBaggageFilter 读取的 header 一致）
                    headers.remove(BaggageKeys.TRACE_ID);
                    headers.add(BaggageKeys.TRACE_ID, traceId);
                })
                .build();

        // 写入 Reactor Context：配合 enableAutomaticContextPropagation，自动同步到 MDC 供日志输出
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .contextWrite(ctx -> ctx.put(BaggageKeys.TRACE_ID, traceId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
