package com.helmsail.seckill.base.mq;

/**
 * 秒杀域 MQ Topic 常量
 */
public final class MqTopic {

    private MqTopic() {}

    /** 秒杀订单消息 */
    public static final String SECKILL_ORDER = "seckill-order-topic";

    /** 自动关单延迟消息 */
    public static final String SECKILL_CLOSE_ORDER = "seckill-close-order-topic";
}
