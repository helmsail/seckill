package com.helmsail.seckill.base.productsku;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动商品SKU实体
 *
 * 物理删除表（不继承 BaseEntity、无逻辑删除），审计字段由自动填充按字段名处理。
 */
@Data
@TableName("sk_product_sku")
public class SeckillProductSku {

    private Long id;
    private String activityNo;
    private String spuNo;
    private String spuName;
    private String skuNo;
    private String skuName;
    private Integer discountType;
    private BigDecimal discountParameter;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer activityStock;
    private Integer purchaseLimit;
    private Integer shelfStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
