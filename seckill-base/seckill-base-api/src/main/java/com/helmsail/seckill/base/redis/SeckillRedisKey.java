package com.helmsail.seckill.base.redis;

/**
 * 秒杀域 Redis Key 常量（唯一权威清单）
 *
 * 命名规范（社区惯例：冒号分层 + 根实例化），模板：
 *   seckill:{根}[:{标识}...][:{资源}:{标识}...][:{属性}]
 *   · 根段 = 数据归属：实体根（activity / user）或流程根（result / deduct / pay）
 *   · 实体数据挂实体根（实例标识紧随根后）：activity:{activityNo}:sku:{skuNo}:stock
 *     ——活动是宿主、SKU 是参与方；同一主域 SKU 出现在多个活动根下即多对多参与
 *   · 流程数据挂流程根：deduct:stock:{traceId}（一次扣减流程的幂等标记）
 *   · 集合资源用复数（skus）；单件资源用单数+标识（sku:{skuNo}）；属性段殿后（stock / shelf / purchase）
 *   · 可变值（活动号 / SKU号 / 用户号 / traceId / orderNo）只出现在标识段，绝不进常量段
 *   · 状态 / 计数用显式值（1 / 0、数字），不用"存在即真"；负标记 = 对象后加 ":null" 段
 *   · 文档中 {x} 仅为变量记号，运行时替换；真实键不出现花括号
 *     （花括号在 Redis Cluster 中为哈希标签——如需"同活动的键同槽"可刻意启用）
 *
 * 分组顺序即业务生命周期：活动快照 → SKU 运行态 → 用户准入 → 秒杀结果 → 消费幂等标记 → 支付
 * 全项目统一经本清单引用，禁止散落字符串硬编码
 */
public final class SeckillRedisKey {

    private SeckillRedisKey() {}

    // ========== 活动快照（缓存同步任务常态刷新；C 端查询读取，miss 回源时兜底回填 / 写负标记） ==========

    /** 活动信息 Hash（field=activityNo；集合索引，非实例资源） */
    public static final String KEY_ACTIVITY_INFO = "seckill:activity:info";

    /** 活动商品SKU列表快照（集合资源用复数；标识：activityNo） */
    public static final String KEY_ACTIVITY_PRODUCT_LIST = "seckill:activity:%s:skus";

    /** 活动不存在负标记（回源确认不存在时写入，短 TTL 自清，跨实例短路回源；值="1"；标识：activityNo） */
    public static final String KEY_ACTIVITY_INFO_NULL = "seckill:activity:%s:info:null";

    /** 活动商品为空负标记（回源确认无 SKU 时写入，短 TTL 自清，跨实例短路回源；值="1"；标识：activityNo） */
    public static final String KEY_ACTIVITY_PRODUCT_NULL = "seckill:activity:%s:skus:null";

    /** 活动列表为空负标记（回源确认无进行中活动时写入，短 TTL 自清，跨实例短路回源；值="1"；全局单键） */
    public static final String KEY_ACTIVITY_LIST_NULL = "seckill:activity:list:null";

    // ========== SKU 运行态与限购上限（预热初始化 / 覆盖；校验侧直读） ==========

    /** SKU 库存计数（参与实例属性；预热初始化，运行期扣减 / 回补；标识：activityNo, skuNo） */
    public static final String KEY_SKU_STOCK = "seckill:activity:%s:sku:%s:stock";

    /** SKU 在售状态（参与实例属性；value=1 上架 / 0 下架；标识：activityNo, skuNo） */
    public static final String KEY_SKU_SHELF = "seckill:activity:%s:sku:%s:shelf";

    /** SKU 限购上限（0=不限购；预热声明式覆盖；校验侧直读，不吃展示缓存；标识：activityNo, skuNo） */
    public static final String KEY_SKU_QUOTA = "seckill:activity:%s:sku:%s:quota";

    // ========== 用户准入（限流 / 限购 / 黑名单） ==========

    /** 用户级限流（用户属性；标识：userId） */
    public static final String KEY_RATE_LIMIT = "seckill:user:%s:rate";

    /** 用户级限购计数（参与实例三方状态：该活动该 SKU 该用户；标识：activityNo, skuNo, userId） */
    public static final String KEY_PURCHASE_LIMIT = "seckill:activity:%s:sku:%s:purchase:%s";

    /** 用户级活动限购计数（活动维度合计：该活动该用户已购总量；标识：activityNo, userId） */
    public static final String KEY_ACTIVITY_PURCHASE_LIMIT = "seckill:activity:%s:purchase:%s";

    /** 用户黑名单（用户属性；标识：userId） */
    public static final String KEY_BLACKLIST = "seckill:user:%s:blacklist";

    // ========== 秒杀结果（processor 回写，C 端轮询读取） ==========

    /** 秒杀结果（流程根；标识：traceId） */
    public static final String KEY_SECKILL_RESULT = "seckill:result:%s";

    // ========== 消费幂等标记（重投重放的扣减去重依据：deduct 原子写入，回滚/终局处理后清理，TTL 24h） ==========

    /** 库存扣减标记（流程根；标识：traceId；value=已扣数量，存在即视为本请求已扣减，重放跳过） */
    public static final String KEY_DEDUCT_STOCK = "seckill:deduct:stock:%s";

    /** 限购扣减标记（流程根；标识：traceId；value=已扣数量，存在即视为本请求已扣减，重放跳过） */
    public static final String KEY_DEDUCT_LIMIT = "seckill:deduct:limit:%s";

    /** 活动级限购扣减标记（流程根；标识：traceId；同 KEY_DEDUCT_LIMIT，用于活动维度合计计数） */
    public static final String KEY_DEDUCT_ACTIVITY_LIMIT = "seckill:deduct:activity-limit:%s";

    // ========== 支付（二维码缓存 / 回调锁） ==========

    /** 支付二维码缓存（流程根；标识：orderNo） */
    public static final String KEY_PAY_QRCODE = "seckill:pay:qrcode:%s";

    /** 支付回调处理锁（流程根；标识：orderNo） */
    public static final String KEY_PAY_LOCK = "seckill:pay:lock:%s";
}
