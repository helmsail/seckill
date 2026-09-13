package com.helmsail.seckill.service.activity;

import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动商品查询视图（C 端列表返回）
 *
 * 基础 DTO 只承载库表/快照形状的字段；实时余量与上下架状态是运行期值
 * （不落库、不进快照），查询时从库存键 / 在售键拼装填充。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityProductVO extends SeckillProductSkuDTO {

    /** 实时余量（查询时从库存键拼装） */
    private Integer remainingStock;
}
