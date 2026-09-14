package com.helmsail.seckill.base.compensation;

/**
 * 补偿记录类型常量（seckill:compensation:pending 记录的 type 字段）
 *
 * compensationJob 按 type 分发执行；admin 侧主域库存归还（ADD_ROLLBACK/REMOVE_RESTORE）
 * 为默认类型（legacy 记录），不经此处常量标识。
 */
public final class CompensationType {

    private CompensationType() {}

    /** 秒杀域库存回补（Redis 库存计数加回；标记守卫幂等，重复执行无副作用） */
    public static final String SECKILL_STOCK = "SECKILL_STOCK";

    /** 秒杀域限购额度回补（Redis 限购计数减回；标记守卫幂等，重复执行无副作用） */
    public static final String SECKILL_LIMIT = "SECKILL_LIMIT";
}
