package com.helmsail.seckill.base.productsku;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 需归还主域的库存项（删除 SKU 后由调用方编排归还）
 */
@Data
@AllArgsConstructor
public class StockRestoreItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String skuNo;

    /** 需归还的库存数量 */
    private int stockToRestore;
}
