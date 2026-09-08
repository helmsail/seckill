package com.helmsail.seckill.common.redis;

/**
 * 秒杀结果状态常量
 */
public final class SeckillResultStatus {

    private SeckillResultStatus() {}

    public static final String PENDING = "pending";
    public static final String PROCESSING = "processing";
    public static final String SUCCESS = "success";
    public static final String FAILED = "failed";
}
