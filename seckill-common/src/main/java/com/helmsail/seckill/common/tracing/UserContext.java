package com.helmsail.seckill.common.tracing;

/**
 * 用户上下文
 *
 * 线程内上下文（普通 ThreadLocal，finally 清理防泄漏）。
 * 当前无异步线程池场景；后续引入异步执行时，需连同 MDC 一起做跨线程传播方案。
 */
public final class UserContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

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
