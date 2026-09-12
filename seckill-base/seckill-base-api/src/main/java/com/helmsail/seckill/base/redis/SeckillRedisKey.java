package com.helmsail.seckill.base.redis;

/**
 * 秒杀域 Redis Key 常量（唯一权威清单）
 *
 * 命名规范：seckill:{域}:{对象}[:{标识}...]
 */
public final class SeckillRedisKey {

    private SeckillRedisKey() {}

    // ========== 活动 ==========

    /** 活动信息 Hash（field=activityNo） */
    public static final String KEY_ACTIVITY_INFO = "seckill:activity:info";

    /** 活动商品SKU列表快照（标识：activityNo） */
    public static final String KEY_ACTIVITY_PRODUCT_LIST = "seckill:activity:products:%s";

    /** 活动在售 SKU 名单（SET，成员=上架 skuNo；标识：activityNo） */
    public static final String KEY_ACTIVITY_SHELF = "seckill:activity:shelf:%s";

    // ========== 库存（运行期权威计数） ==========

    /** SKU 库存计数（标识：activityNo:skuNo） */
    public static final String KEY_SKU_STOCK = "seckill:sku:stock:%s:%s";

    /** SKU 库存初始总量（restore 上界参照，预热写入后不再变更；标识：activityNo:skuNo） */
    public static final String KEY_SKU_STOCK_TOTAL = "seckill:sku:stock:total:%s:%s";

    /** SKU 库存归还完成标记（终态清理跨轮幂等依据，写入后长期保留；标识：activityNo:skuNo） */
    public static final String KEY_SKU_STOCK_RESTORED = "seckill:sku:stock:restored:%s:%s";

    // ========== 限流 / 限购 / 黑名单 ==========

    /** 用户级限流（标识：userId） */
    public static final String KEY_RATE_LIMIT = "seckill:limit:rate:%s";

    /** 用户级限购计数（标识：activityNo:skuNo:userId） */
    public static final String KEY_PURCHASE_LIMIT = "seckill:limit:purchase:%s:%s:%s";

    /** 用户黑名单（标识：userId） */
    public static final String KEY_BLACKLIST = "seckill:blacklist:%s";

    // ========== 秒杀结果 ==========

    /** 秒杀结果（标识：traceId） */
    public static final String KEY_SECKILL_RESULT = "seckill:result:%s";

    // ========== 支付 ==========

    /** 支付二维码缓存（标识：orderNo） */
    public static final String KEY_PAY_QRCODE = "seckill:pay:qrcode:%s";

    /** 支付回调处理锁（标识：orderNo） */
    public static final String KEY_PAY_LOCK = "seckill:pay:lock:%s";

    // ========== 故障落档 / 补偿（运维兜底） ==========

    /** 系统异常落档（标识：traceId）：技术异常导致秒杀失败时保留 30 天，供排查/对账 */
    public static final String KEY_SECKILL_FAIL_SYSTEM = "seckill:fail:system:%s";

    /** 待补偿库存归还（Hash，field=类型:活动:SKU，value=JSON 明细；compensationJob 消费） */
    public static final String KEY_COMPENSATION_PENDING = "seckill:compensation:pending";

    /** 补偿重试超限转人工（Hash，field 与 pending 一致） */
    public static final String KEY_COMPENSATION_FAILED = "seckill:compensation:failed";
}
