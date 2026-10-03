package com.helmsail.seckill.base.id;

import com.helmsail.seckill.common.id.BusinessId;
import lombok.Getter;

/**
 * 秒杀域业务 ID
 */
@Getter
public enum SeckillBusinessId implements BusinessId {

    SECKILL_ACTIVITY(3, "秒杀活动编号"),
    SECKILL_ORDER(4, "秒杀订单编号");

    private final int prefix;
    private final String desc;

    SeckillBusinessId(int prefix, String desc) {
        this.prefix = prefix;
        this.desc = desc;
    }
}
