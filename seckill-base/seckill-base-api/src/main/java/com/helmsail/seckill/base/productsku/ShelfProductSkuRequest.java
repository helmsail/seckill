package com.helmsail.seckill.base.productsku;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量上架/下架请求（活动非终态可用）
 */
@Data
public class ShelfProductSkuRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 活动编号 */
    private String activityNo;

    /** 待操作的 SKU 编号列表 */
    private List<String> skuNos;

    /** true=上架，false=下架 */
    private boolean onShelf;
}
