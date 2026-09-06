package com.helmsail.seckill.common.exception;

import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.common.result.ResultEnum;
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
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebFluxExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Mono<Result<?>> handleBizException(BizException e) {
        return Mono.just(Result.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Mono<Result<?>> handleException(Exception e) {
        return Mono.just(Result.of(ResultEnum.SYSTEM_ERROR));
    }
}
