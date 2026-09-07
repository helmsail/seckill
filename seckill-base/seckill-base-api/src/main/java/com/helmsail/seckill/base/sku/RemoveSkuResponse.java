package com.helmsail.seckill.base.sku;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 删除 SKU 响应
 */
@Data
@AllArgsConstructor
public class RemoveSkuResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SKU 编号 */
    private String skuNo;

    /** 需要归还的库存数量 */
    private int stockToRestore;

    /** 需要归还的原价（用于恢复主域库存） */
    private BigDecimal originalPrice;
}
