package com.helmsail.seckill.support.server.id;

import com.helmsail.seckill.common.id.BusinessPrefix;
import lombok.Getter;

/**
 * 支撑模块业务前缀
 */
@Getter
public enum SupportBusinessPrefix implements BusinessPrefix {

    PRODUCT(1, "商品编号前缀"),
    SKU(2, "SKU编号前缀");

    private final int prefix;
    private final String desc;

    SupportBusinessPrefix(int prefix, String desc) {
        this.prefix = prefix;
        this.desc = desc;
    }
}
