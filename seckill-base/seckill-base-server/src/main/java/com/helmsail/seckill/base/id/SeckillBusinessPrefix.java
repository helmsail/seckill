package com.helmsail.seckill.base.id;

import com.helmsail.seckill.common.id.BusinessPrefix;
import lombok.Getter;

/**
 * 秒杀域业务前缀
 */
@Getter
public enum SeckillBusinessPrefix implements BusinessPrefix {

    SECKILL_ACTIVITY(3, "秒杀活动编号前缀"),
    SECKILL_ORDER(4, "秒杀订单编号前缀");

    private final int prefix;
    private final String desc;

    SeckillBusinessPrefix(int prefix, String desc) {
        this.prefix = prefix;
        this.desc = desc;
    }
}
