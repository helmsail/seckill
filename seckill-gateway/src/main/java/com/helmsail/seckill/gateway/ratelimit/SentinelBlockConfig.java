package com.helmsail.seckill.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.gateway.exception.GatewayError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Sentinel 限流响应配置
 *
 * 覆盖 Sentinel 默认的纯文本响应，被限流时返回项目统一的 Result JSON（429）。
 *
 * 独立于 GatewayExceptionHandler：限流不是异常，而是 Sentinel 在正常流程中的主动拒绝，
 * 由 SentinelGatewayBlockExceptionHandler（WebExceptionHandler 契约）直接回调本 Bean 输出响应，
 * 与 ErrorWebExceptionHandler 是两条互不干涉的链路。
 */
@Configuration
public class SentinelBlockConfig {

    @Bean
    public BlockRequestHandler blockRequestHandler() {
        return (exchange, throwable) -> ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.of(GatewayError.RATE_LIMITED));
    }
}
