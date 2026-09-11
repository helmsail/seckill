package com.helmsail.seckill.common.id;

/**
 * 业务前缀接口
 *
 * 各模块实现此接口定义自己的业务前缀。
 */
public interface BusinessPrefix {

    /**
     * 前缀（数字）
     */
    int getPrefix();

    /**
     * 描述
     */
    String getDesc();

    /**
     * 业务编号 = 前缀 + 雪花 ID
     */
    default String buildNo(long snowflakeId) {
        return getPrefix() + String.valueOf(snowflakeId);
    }
}
