package com.helmsail.seckill.base.product;

import com.baomidou.mybatisplus.annotation.TableName;
import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 秒杀商品实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sk_product")
public class SeckillProduct extends BaseEntity {

    private Long id;
    private String activityNo;
    private String spuNo;
    private String spuName;
    private Integer discountType;
    private BigDecimal discountParameter;
    private Integer sortOrder;
}
