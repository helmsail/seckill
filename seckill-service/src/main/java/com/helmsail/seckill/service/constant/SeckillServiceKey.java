package com.helmsail.seckill.service.constant;

/**
 * 秒杀服务 Redis Key 常量
 */
public final class SeckillServiceKey {

    private SeckillServiceKey() {}

    public static final String KEY_RATE_LIMIT = "seckill:rate:limit:%s";
    public static final String KEY_BLACKLIST = "seckill:blacklist:%s";
    public static final String KEY_SECKILL_RESULT = "seckill:result:%s";
}
