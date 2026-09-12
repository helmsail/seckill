package com.helmsail.seckill.common.exception;

import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * WebMvc 全局异常处理器
 *
 * 仅在 Servlet 环境下生效。
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebMvcExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.of(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        log.warn("参数校验异常: {}", message);
        return Result.of(ResultEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 请求体解析失败（字段类型/格式非法）：返回参数错误而非系统异常，避免混淆业务故障与非法入参
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.of(ResultEnum.PARAM_ERROR.getCode(), "请求体格式错误，请检查字段类型与格式");
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 跨进程业务异常可能被 Dubbo 包装为 RpcException（业务异常藏在 cause 链中），解包恢复业务错误码
        BizException bizException = findBizException(e.getCause());
        if (bizException != null) {
            log.warn("业务异常(解包): code={}, message={}", bizException.getCode(), bizException.getMessage());
            return Result.of(bizException.getCode(), bizException.getMessage());
        }
        log.error("系统异常", e);
        return Result.of(ResultEnum.SYSTEM_ERROR);
    }

    /**
     * 沿 cause 链查找 BizException
     */
    private static BizException findBizException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof BizException bizException) {
                return bizException;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }
}
