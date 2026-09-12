package com.helmsail.seckill.gateway.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



/**
 * 网关全局异常处理
 *
 * 实现 ErrorWebExceptionHandler（Spring WebFlux 官方异常处理契约），
 * 统一处理路由转发异常，返回 JSON 格式响应。
 *
 * 优先级低于 Sentinel 的 BlockExceptionHandler（限流异常由 Sentinel 处理），
 * 其余异常经 Sentinel 透传后由本类处理。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        // 防御性透传：限流异常交给 Sentinel 自己的处理器
        if (ex instanceof BlockException) {
            return Mono.error(ex);
        }

        HttpStatus httpStatus;
        Result<Void> result;

        if (ex instanceof ResponseStatusException rse) {
            httpStatus = HttpStatus.valueOf(rse.getStatusCode().value());
            String message = rse.getReason() != null ? rse.getReason() : "请求处理失败";
            result = Result.of(GatewayError.BAD_GATEWAY.getCode(), message);
        } else {
            httpStatus = HttpStatus.BAD_GATEWAY;
            result = Result.of(GatewayError.BAD_GATEWAY);
            log.error("网关异常: path={}, error={}", exchange.getRequest().getURI().getPath(), ex.getMessage(), ex);
        }

        exchange.getResponse().setStatusCode(httpStatus);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
