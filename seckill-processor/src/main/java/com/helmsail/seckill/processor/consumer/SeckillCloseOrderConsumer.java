package com.helmsail.seckill.processor.consumer;

import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 自动关单消费者
 *
 * 接收延迟消息，检查订单是否未支付，未支付则关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopic.SECKILL_CLOSE_ORDER, consumerGroup = "seckill-close-order-consumer-group")
public class SeckillCloseOrderConsumer implements RocketMQListener<MessageExt> {

    @DubboReference
    private SeckillOrderService seckillOrderService;

    @Override
    public void onMessage(MessageExt message) {
        BaggageUtils.restore(message.getProperties());
        try {
            String orderNo = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("收到自动关单消息: orderNo={}", orderNo);

            try {
                seckillOrderService.closeOrder(orderNo);
                log.info("订单已关闭: orderNo={}", orderNo);
            } catch (Exception e) {
                log.error("关单失败: orderNo={}", orderNo, e);
            }
        } finally {
            BaggageUtils.clear();
        }
    }
}
