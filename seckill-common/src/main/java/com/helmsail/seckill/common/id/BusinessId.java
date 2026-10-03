package com.helmsail.seckill.common.id;

/**
 * 业务 ID 接口
 *
 * 各模块实现此接口定义自己的业务 ID 类型；
 * 业务编号 = 前缀 + 雪花 ID，统一经 {@link #buildNo()} 生成完整编号。
 */
public interface BusinessId {

    /**
     * 前缀（数字）
     */
    int getPrefix();

    /**
     * 描述
     */
    String getDesc();

    /**
     * 业务编号 = 前缀 + 新生成的雪花 ID
     */
    default String buildNo() {
        return getPrefix() + String.valueOf(SnowflakeIdGenerator.nextId());
    }
}
