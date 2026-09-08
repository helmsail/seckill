package com.helmsail.seckill.base.redis;

/**
 * 秒杀域 Redis 缓存 Key 常量
 */
public final class SeckillCacheKey {

    private SeckillCacheKey() {}

    public static final String KEY_ACTIVITY_INFO = "seckill:activity:info";
    public static final String KEY_ACTIVITY_PRODUCT_LIST = "seckill:activity:product:list:%s";
    public static final String KEY_SKU_STOCK = "seckill:sku:stock:%s";
}
