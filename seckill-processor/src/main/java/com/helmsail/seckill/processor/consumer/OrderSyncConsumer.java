package com.helmsail.seckill.processor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqGroup;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.SeckillOrderSyncEvent;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import com.helmsail.seckill.support.api.order.CreateOrderRequest;
import com.helmsail.seckill.support.api.order.OrderService;
import com.helmsail.seckill.support.api.order.OrderSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订单同步消费者（秒杀域 → 主域）
 *
 * 仅接收支付成功的订单事件；经 Dubbo 写入主域（support 保持纯 Dubbo 被调方）。
 * 失败抛异常触发 MQ 重投；重复投递由主域 create 的 orderNo 幂等兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.ORDER_SYNC,
        consumerGroup = MqGroup.ORDER_SYNC_CONSUMER
)
public class OrderSyncConsumer implements RocketMQListener<MessageExt> {

    @DubboReference
    private OrderService supportOrderService;

    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt message) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            BaggageUtils.restore(message.getProperties());
            SeckillOrderSyncEvent event = objectMapper.readValue(json, SeckillOrderSyncEvent.class);
            supportOrderService.create(buildRequest(event));
            log.info("秒杀订单同步主域完成: orderNo={}", event.getOrderNo());
        } catch (Exception e) {
            log.error("秒杀订单同步失败: {}", json, e);
            throw new RuntimeException("订单同步失败", e);
        } finally {
            BaggageUtils.clear();
        }
    }

    private CreateOrderRequest buildRequest(SeckillOrderSyncEvent event) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setOrderNo(event.getOrderNo());
        request.setUserId(event.getUserId());
        request.setOrderSource(OrderSource.SECKILL);
        request.setTotalAmount(event.getTotalAmount());
        request.setPayAmount(event.getPayAmount());
        request.setPaidTime(event.getPaidTime());
        request.setTradeNo(event.getTradeNo());
        return request;
    }
}
