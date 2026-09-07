package com.helmsail.seckill.service.tracing;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 用户上下文
 *
 * 基于 TTL，支持跨线程传播。
 */
public final class UserContext {

    private static final TransmittableThreadLocal<String> USER_ID = new TransmittableThreadLocal<>();

    public static String currentUserId() {
        String val = USER_ID.get();
        return val != null ? val : "N/A";
    }

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static void clear() {
        USER_ID.remove();
    }
}
