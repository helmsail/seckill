package com.helmsail.seckill.base.redis;

/**
 * 秒杀域 Redis Key 常量（唯一权威清单）
 *
 * 命名规范：seckill:{域}:{对象}[:{标识}...]
 * 分组顺序即业务生命周期：活动快照 → 库存计数 → 用户准入 → 秒杀结果 → 支付 → 补偿
 * 全项目统一经本清单引用，禁止散落字符串硬编码
 */
public final class SeckillRedisKey {

    private SeckillRedisKey() {}

    // ========== 活动快照（预热写入，C 端查询读取；终态由刷新任务清理） ==========

    /** 活动信息 Hash（field=activityNo） */
    public static final String KEY_ACTIVITY_INFO = "seckill:activity:info";

    /** 活动商品SKU列表快照（标识：activityNo） */
    public static final String KEY_ACTIVITY_PRODUCT_LIST = "seckill:activity:products:%s";

    /** 活动在售 SKU 名单（SET，成员=上架 skuNo；标识：activityNo） */
    public static final String KEY_ACTIVITY_SHELF = "seckill:activity:shelf:%s";

    // ========== 库存计数（运行期权威：预热初始化，扣减 / 回补 / 终态归还） ==========

    /** SKU 库存计数（预热初始化，运行期扣减 / 回补；标识：activityNo:skuNo） */
    public static final String KEY_SKU_STOCK = "seckill:sku:stock:%s:%s";

    /** SKU 库存归还完成标记（终态清理跨轮幂等依据，写入后长期保留；标识：activityNo:skuNo） */
    public static final String KEY_SKU_STOCK_RESTORED = "seckill:sku:stock:restored:%s:%s";

    // ========== 用户准入（限流 / 限购 / 黑名单） ==========

    /** 用户级限流（标识：userId） */
    public static final String KEY_RATE_LIMIT = "seckill:limit:rate:%s";

    /** 用户级限购计数（标识：activityNo:skuNo:userId） */
    public static final String KEY_PURCHASE_LIMIT = "seckill:limit:purchase:%s:%s:%s";

    /** 用户黑名单（标识：userId） */
    public static final String KEY_BLACKLIST = "seckill:blacklist:%s";

    // ========== 秒杀结果（processor 回写，C 端轮询读取） ==========

    /** 秒杀结果（标识：traceId） */
    public static final String KEY_SECKILL_RESULT = "seckill:result:%s";

    // ========== 支付（二维码缓存 / 回调锁） ==========

    /** 支付二维码缓存（标识：orderNo） */
    public static final String KEY_PAY_QRCODE = "seckill:pay:qrcode:%s";

    /** 支付回调处理锁（标识：orderNo） */
    public static final String KEY_PAY_LOCK = "seckill:pay:lock:%s";

    // ========== 补偿（运维兜底：pending 待消费，failed 转人工） ==========

    /** 待补偿库存归还（Hash，field=类型:活动:SKU，value=JSON 明细；compensationJob 消费） */
    public static final String KEY_COMPENSATION_PENDING = "seckill:compensation:pending";

    /** 补偿重试超限转人工（Hash，field 与 pending 一致） */
    public static final String KEY_COMPENSATION_FAILED = "seckill:compensation:failed";
}
