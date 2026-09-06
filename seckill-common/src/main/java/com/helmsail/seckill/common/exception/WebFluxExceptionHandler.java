package com.helmsail.seckill.common.exception;

import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

/**
 * WebFlux 全局异常处理器
 *
 * 仅在 Reactive 环境下生效。
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebFluxExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Mono<Result<?>> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Mono.just(Result.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Mono<Result<?>> handleException(Exception e) {
        log.error("系统异常", e);
        return Mono.just(Result.of(ResultEnum.SYSTEM_ERROR));
    }
}
