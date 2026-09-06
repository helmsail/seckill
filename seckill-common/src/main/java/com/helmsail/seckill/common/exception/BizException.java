package com.helmsail.seckill.common.exception;

import com.helmsail.seckill.common.result.IResultCode;
import lombok.Getter;

/**
 * 业务异常
 *
 * 秒杀系统中不填写堆栈，提升性能。
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String message;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BizException(IResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
