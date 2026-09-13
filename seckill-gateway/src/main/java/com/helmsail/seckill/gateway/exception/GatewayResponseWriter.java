package com.helmsail.seckill.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关 JSON 响应写入工具
 *
 * 统一过滤器/异常处理器的"直接写回响应"逻辑：
 * 设置状态码 + JSON Content-Type，序列化统一 Result 结构后写入。
 * 供 AuthenticationGlobalFilter / AuthorizationGlobalFilter / GatewayExceptionHandler 复用。
 */
@Slf4j
public final class GatewayResponseWriter {

    private GatewayResponseWriter() {}

    public static Mono<Void> write(ServerWebExchange exchange, ObjectMapper objectMapper,
                                   HttpStatus status, Result<?> body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            log.error("网关响应序列化失败: status={}, error={}", status, e.getMessage());
            return response.setComplete();
        }
    }
}
