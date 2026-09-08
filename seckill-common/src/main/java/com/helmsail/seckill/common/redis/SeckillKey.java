package com.helmsail.seckill.common.redis;

public final class SeckillKey {

    private SeckillKey() {}

    public static final String KEY_RATE_LIMIT = "seckill:rate:limit:%s";
    public static final String KEY_BLACKLIST = "seckill:blacklist:%s";
    public static final String KEY_SECKILL_RESULT = "seckill:result:%s";
}
