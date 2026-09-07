package com.helmsail.seckill.base.sku;

import lombok.Data;

import java.io.Serializable;

/**
 * 删除 SKU 请求
 */
@Data
public class RemoveSkuRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 秒杀商品 ID */
    private String skProductId;

    /** SKU 编号（主域） */
    private String skuNo;

    /** 活动状态校验（外部传入，只有满足此状态才能删除） */
    private Integer requiredStatus;
}
