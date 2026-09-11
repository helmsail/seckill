package com.helmsail.seckill.common.tracing;

/**
 * 链路追踪键名常量
 */
public final class BaggageKeys {

    private BaggageKeys() {}

    /** 链路追踪 ID */
    public static final String TRACE_ID = "traceId";

    /** 用户 ID */
    public static final String USER_ID = "userId";
}
