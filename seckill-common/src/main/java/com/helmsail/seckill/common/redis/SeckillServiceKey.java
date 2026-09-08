package com.helmsail.seckill.common.redis;

/**
 * 秒杀服务 Redis Key 常量
 */
public final class SeckillServiceKey {

    private SeckillServiceKey() {}

    // ========== Redis Key ==========
    public static final String KEY_RATE_LIMIT = "seckill:rate:limit:%s";
    public static final String KEY_BLACKLIST = "seckill:blacklist:%s";
    public static final String KEY_SECKILL_RESULT = "seckill:result:%s";

    // ========== 秒杀结果状态 ==========
    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
}
