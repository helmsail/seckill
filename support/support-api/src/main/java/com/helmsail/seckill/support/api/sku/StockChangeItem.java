package com.helmsail.seckill.support.api.sku;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 库存变更项（批量扣减/归还的最小单元）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockChangeItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** SKU 编号 */
    private String skuNo;

    /** 变更数量（正数） */
    private int quantity;
}
