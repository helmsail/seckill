package com.helmsail.seckill.gateway.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 网关错误码
 *
 * 仅包含网关层面的错误，不涉及业务错误。
 */
@Getter
@AllArgsConstructor
public enum GatewayError {

    UNAUTHORIZED("unauthorized", "未认证"),
    FORBIDDEN("forbidden", "无权限"),
    RATE_LIMITED("rate_limited", "请求过于频繁"),
    BAD_GATEWAY("bad_gateway", "下游服务不可用");

    private final String code;
    private final String message;
}
