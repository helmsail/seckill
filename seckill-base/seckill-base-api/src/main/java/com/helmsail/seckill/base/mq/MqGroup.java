package com.helmsail.seckill.base.mq;

/**
 * 秒杀域 MQ 消费组常量
 *
 * 命名规范：{域}-{用途}，集中声明供 @RocketMQMessageListener 引用。
 */
public final class MqGroup {

    private MqGroup() {}

    /** 秒杀订单处理消费组 */
    public static final String SECKILL_ORDER_CONSUMER = "seckill-order-consumer";

    /** 自动关单消费组 */
    public static final String SECKILL_CLOSE_ORDER_CONSUMER = "seckill-close-order-consumer";

    /** 订单同步消费组（主域订单归档） */
    public static final String ORDER_SYNC_CONSUMER = "seckill-order-sync-consumer";
}
