package com.helmsail.seckill.base.redis;

/**
 * 秒杀系统 Redis Key 常量
 *
 * 命名规则：seckill:{module}:{entity}[:{detail}]
 * 常量规则：KEY_{MODULE}_{ENTITY}[_{DETAIL}]
 */
public final class SeckillKey {

    private SeckillKey() {}

    /**
     * 活动信息（Hash）
     * Key: seckill:activity:info
     * Field: {activityNo}
     * Value: 活动信息 JSON
     *
     * 包含所有活动（待开始、进行中、已暂停、已结束）
     */
    public static final String KEY_ACTIVITY_INFO = "seckill:activity:info";

    /**
     * 活动商品列表
     * 格式: seckill:activity:product:list:{activityNo}
     * Value: 商品+SKU 聚合 JSON
     */
    public static final String KEY_ACTIVITY_PRODUCT_LIST = "seckill:activity:product:list:%s";

    /**
     * SKU 库存
     * 格式: seckill:sku:stock:{skuId}
     * Value: 库存数量
     */
    public static final String KEY_SKU_STOCK = "seckill:sku:stock:%s";
}
