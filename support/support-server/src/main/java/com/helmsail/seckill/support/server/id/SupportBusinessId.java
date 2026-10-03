package com.helmsail.seckill.support.server.id;

import com.helmsail.seckill.common.id.BusinessId;
import lombok.Getter;

/**
 * 支撑模块业务 ID
 */
@Getter
public enum SupportBusinessId implements BusinessId {

    PRODUCT(1, "商品编号"),
    SKU(2, "SKU编号");

    private final int prefix;
    private final String desc;

    SupportBusinessId(int prefix, String desc) {
        this.prefix = prefix;
        this.desc = desc;
    }
}
