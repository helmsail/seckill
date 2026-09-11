package com.helmsail.seckill.base.productsku;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量添加活动商品SKU请求（仅待开始状态可用）
 *
 * 主域库存划拨由调用方先行完成；本服务失败时调用方负责补偿归还。
 */
@Data
public class AddProductSkuRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 活动编号 */
    private String activityNo;

    /** 待添加的 SKU 配置列表 */
    private List<Item> items;

    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** SPU 编号（快照） */
        private String spuNo;

        /** SPU 名称快照 */
        private String spuName;

        /** SKU 编号（主域） */
        private String skuNo;

        /** SKU 名称快照 */
        private String skuName;

        /** 原价 */
        private BigDecimal originalPrice;

        /** 折扣类型 */
        private DiscountType discountType;

        /** 折扣参数 */
        private BigDecimal discountParameter;

        /** 划拨到活动的秒杀库存 */
        private Integer activityStock;

        /** SKU 级限购（0=不限购） */
        private Integer purchaseLimit;
    }
}
