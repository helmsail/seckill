package com.helmsail.seckill.base.redis;

/**
 * 秒杀域 Redis 缓存 Key 常量
 */
public final class SeckillCacheKey {

    private SeckillCacheKey() {}

    public static final String KEY_ACTIVITY_INFO = "seckill:activity:info";
    public static final String KEY_ACTIVITY_PRODUCT_LIST = "seckill:activity:product:list:%s";
    /** 活动在售 SKU 名单（SET，成员=上架 skuNo） */
    public static final String KEY_ACTIVITY_SHELF = "seckill:activity:shelf:%s";
    /** SKU 库存计数（activityNo + skuNo 定位） */
    public static final String KEY_SKU_STOCK = "seckill:sku:stock:%s:%s";
}
