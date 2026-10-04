package com.helmsail.seckill.admin.vo;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 活动详情 VO（活动 + 秒杀域 SKU 平铺列表）
 *
 * SPU 分组塑形（分组标题行 + 组级折扣信息）由前端完成，本 VO 只做简单拼装。
 */
@Data
@AllArgsConstructor
public class ActivityDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 活动信息 */
    private ActivityDTO activity;

    /** 商品 SKU 平铺列表（按 id 升序，与展示顺序一致） */
    private List<SeckillProductSkuDTO> skus;
}
