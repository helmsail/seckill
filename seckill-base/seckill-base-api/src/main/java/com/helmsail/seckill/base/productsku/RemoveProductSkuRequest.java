package com.helmsail.seckill.base.productsku;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量删除活动商品SKU请求（仅待开始状态可用，物理删除）
 */
@Data
public class RemoveProductSkuRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 活动编号 */
    private String activityNo;

    /** 待删除的 SKU 编号列表 */
    private List<String> skuNos;
}
