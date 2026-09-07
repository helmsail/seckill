package com.helmsail.seckill.base.product;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 活动添加商品请求
 */
@Data
public class AddProductRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 活动编号 */
    private String activityNo;

    /** SPU 编号（溯源） */
    private String spuNo;

    /** 商品名称快照 */
    private String spuName;

    /** 折扣类型 */
    private DiscountType discountType;

    /** 折扣参数 */
    private BigDecimal discountParameter;

    /** 排序顺序 */
    private Integer sortOrder;
}
