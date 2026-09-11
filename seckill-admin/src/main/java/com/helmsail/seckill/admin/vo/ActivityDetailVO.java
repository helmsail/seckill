package com.helmsail.seckill.admin.vo;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 活动详情 VO（层次化输出：活动 → SPU 分组 → SKU 行）
 */
@Data
@AllArgsConstructor
public class ActivityDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 活动信息 */
    private ActivityDTO activity;

    /** 商品及 SKU 列表 */
    private List<ProductWithSkus> products;

    @Data
    @AllArgsConstructor
    public static class ProductWithSkus implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商品信息 */
        private ProductInfo product;

        /** SKU 列表 */
        private List<SeckillProductSkuDTO> skus;
    }

    @Data
    @AllArgsConstructor
    public static class ProductInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        private String activityNo;
        private String spuNo;
        private String spuName;
        private String discountType;
        private BigDecimal discountParameter;
    }
}
