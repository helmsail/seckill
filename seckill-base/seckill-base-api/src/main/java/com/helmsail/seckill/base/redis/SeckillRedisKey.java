package com.helmsail.seckill.base.redis;

/**
 * 秒杀域 Redis Key 常量（唯一权威清单）
 *
 * 命名规范：seckill:{域}:{对象}[:{标识}...]
 * 分组顺序即业务生命周期：活动快照 → SKU 运行态 → 用户准入 → 秒杀结果 → 消费幂等标记 → 支付 → 补偿
 * 全项目统一经本清单引用，禁止散落字符串硬编码
 */
public final class SeckillRedisKey {

    private SeckillRedisKey() {}

    // ========== 活动快照（缓存同步任务写入，C 端查询读取；终态由回收任务清理） ==========

    /** 活动信息 Hash（field=activityNo） */
    public static final String KEY_ACTIVITY_INFO = "seckill:activity:info";

    /** 活动商品SKU列表快照（标识：activityNo） */
    public static final String KEY_ACTIVITY_PRODUCT_LIST = "seckill:activity:products:%s";

    // ========== SKU 运行态（预热初始化；库存计数扣减 / 回补 / 终态归还；在售状态随上下架覆盖） ==========

    /** SKU 库存计数（预热初始化，运行期扣减 / 回补；标识：activityNo:skuNo） */
    public static final String KEY_SKU_STOCK = "seckill:sku:stock:%s:%s";

    /** SKU 在售状态（预热初始化，运行期随上下架覆盖；value=1 上架 / 0 下架；标识：activityNo:skuNo） */
    public static final String KEY_SKU_SHELF = "seckill:sku:shelf:%s:%s";

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

    // ========== 消费幂等标记（重投重放的扣减去重依据：deduct 原子写入，回滚/终局处理后清理，TTL 24h） ==========

    /** 库存扣减标记（标识：traceId；value=已扣数量，存在即视为本请求已扣减，重放跳过） */
    public static final String KEY_DEDUCT_STOCK = "seckill:deduct:stock:%s";

    /** 限购扣减标记（标识：traceId；value=已扣数量，存在即视为本请求已扣减，重放跳过） */
    public static final String KEY_DEDUCT_LIMIT = "seckill:deduct:limit:%s";

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
