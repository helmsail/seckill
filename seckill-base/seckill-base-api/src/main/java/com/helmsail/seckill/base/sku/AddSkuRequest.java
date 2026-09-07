package com.helmsail.seckill.base.sku;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 添加 SKU 请求
 */
@Data
public class AddSkuRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 秒杀商品 ID */
    private String skProductId;

    /** SKU 编号（主域） */
    private String skuNo;

    /** SKU 名称快照 */
    private String skuName;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 秒杀库存 */
    private Integer activityStock;

    /** SKU 级别限购 */
    private Integer purchaseLimit;

    /** 活动状态校验（外部传入，只有满足此状态才能添加） */
    private Integer requiredStatus;

    /** 是否已从主域扣减库存（外部传入，true 表示已完成扣减） */
    private boolean stockDeducted;
}
