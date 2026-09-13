package com.helmsail.seckill.service.activity;

import com.helmsail.seckill.base.productsku.DiscountType;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 活动商品查询视图（C 端列表返回）
 *
 * 独立视图，不携带总库存、库表主键等内部字段：目录字段来自快照 / 回源，
 * 运行态字段（实时余量、上下架）由库存键 / 在售键值替换填充。
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
    private Integer purchaseLimit;

    /** 实时余量（查询时从库存键替换填充） */
    private Integer remainingStock;
    /** 上下架状态：0=下架，1=上架（查询时从在售键替换填充） */
    private Integer shelfStatus;
}
