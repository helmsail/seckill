package com.helmsail.seckill.service.activity;

import com.helmsail.seckill.base.productsku.DiscountType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 活动商品查询视图（C 端列表返回）
 *
 * 独立视图，不携带总库存、库表主键等内部字段：目录字段来自快照 / 回源，
 * 实时余量由库存键实时替换填充；上下架随缓存承载（5s 异步刷新对齐独立键，缺失按 0 与门禁同口径）；
 * 限购上限属校验数据（quota 键），不在展示视图。
 */
@Data
public class ActivityProductVO {

    private String activityNo;
    private String spuNo;
    private String spuName;
    private String skuNo;
    private String skuName;
    private DiscountType discountType;
    private BigDecimal discountParameter;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;

    /** 实时余量（查询时从库存键替换填充） */
    private Integer remainingStock;
    /** 上下架状态：0=下架，1=上架（随缓存，5s 异步刷新对齐独立键；缺失按 0，与购买门禁同口径） */
    private Integer shelfStatus;
}
