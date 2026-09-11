package com.helmsail.seckill.processor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqGroup;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.helmsail.seckill.base.order.SeckillOrderStatus;
import com.helmsail.seckill.base.order.SeckillOrderSyncEvent;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import com.helmsail.seckill.processor.seckill.PurchaseLimitService;
import com.helmsail.seckill.processor.seckill.StockService;
import com.helmsail.seckill.support.api.pay.PayChannelType;
import com.helmsail.seckill.support.api.pay.PayService;
import com.helmsail.seckill.support.api.pay.PayTradeResult;
import com.helmsail.seckill.support.api.pay.PayTradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 自动关单消费者
 *
 * 接收延迟消息：待支付订单超时未付则关闭，并回补秒杀域库存与限购额度。
 * 关闭前先向渠道查单：已支付则补记支付（防回调丢失导致"付了钱被关单"）。
 * 关单异常会抛出以触发 MQ 重试；回补失败记录日志待人工核对（回补为幂等的条件触发）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopic.SECKILL_CLOSE_ORDER, consumerGroup = MqGroup.SECKILL_CLOSE_ORDER_CONSUMER)
public class SeckillCloseOrderConsumer implements RocketMQListener<MessageExt> {

    @DubboReference
    private SeckillOrderService seckillOrderService;

    @DubboReference
    private PayService supportPayService;

    private final StockService stockService;
    private final PurchaseLimitService purchaseLimitService;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt message) {
        BaggageUtils.restore(message.getProperties());
        try {
            String orderNo = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("收到自动关单消息: orderNo={}", orderNo);

            SeckillOrderDTO order;
            try {
                order = seckillOrderService.getByOrderNo(orderNo);
            } catch (BizException e) {
                // 订单不存在属永久错误，不重试
                log.warn("关单跳过，订单不存在: orderNo={}", orderNo);
                return;
            }
            if (order.getOrderStatus() != SeckillOrderStatus.PENDING) {
                log.info("关单跳过，订单状态非待支付: orderNo={}, status={}", orderNo, order.getOrderStatus());
                return;
            }

            // 关单前查单：渠道已支付则补记支付，不再关单（查单/补记异常抛出触发 MQ 重试）
            if (compensateIfPaid(order)) {
                return;
            }

            // 条件关闭：仅 PENDING 可关闭；异常抛出触发 MQ 重试（重复消费幂等）
            boolean closed = seckillOrderService.closeOrder(orderNo);
            if (!closed) {
                log.info("关单跳过（并发已处理）: orderNo={}", orderNo);
                return;
            }

            // 回补秒杀域资源（best-effort，失败记录待人工核对）
            restoreResources(order);

            log.info("订单已关闭并回补资源: orderNo={}, activityNo={}, skuNo={}, quantity={}",
                    orderNo, order.getActivityNo(), order.getSkuNo(), order.getQuantity());
        } finally {
            BaggageUtils.clear();
        }
    }

    private void restoreResources(SeckillOrderDTO order) {
        try {
            stockService.restore(order.getActivityNo(), order.getSkuNo(), order.getQuantity());
        } catch (Exception e) {
            log.error("关单回补库存失败，需人工核对: orderNo={}, activityNo={}, skuNo={}, quantity={}",
                    order.getOrderNo(), order.getActivityNo(), order.getSkuNo(), order.getQuantity(), e);
        }
        try {
            purchaseLimitService.restore(order.getActivityNo(), order.getSkuNo(),
                    String.valueOf(order.getUserId()), order.getQuantity());
        } catch (Exception e) {
            log.error("关单回补限购失败，需人工核对: orderNo={}", order.getOrderNo(), e);
        }
    }

    /**
     * 关单前查单补偿：渠道已支付则补记本地支付并同步主域
     *
     * 与真实回调并发的竞态由 base 条件更新与主域幂等兜底。
     *
     * @return true 表示订单已转为支付成功（不应再关单）
     */
    private boolean compensateIfPaid(SeckillOrderDTO order) {
        // TODO 当前仅 Mock 渠道；接入多渠道后需按订单所属渠道查询
        PayTradeResult trade = supportPayService.queryTrade(PayChannelType.MOCK, order.getOrderNo());
        if (trade == null || !PayTradeStatus.PAID.equals(trade.getTradeStatus())) {
            return false;
        }
        log.warn("关单前查单发现已支付，转补记支付: orderNo={}, tradeNo={}",
                order.getOrderNo(), trade.getTradeNo());
        seckillOrderService.paySuccess(order.getOrderNo(), trade.getTradeNo());
        sendOrderSync(order, trade.getTradeNo());
        return true;
    }

    /**
     * 补记支付后同步主域（与 service 回调链路同一事件；发送失败由对账任务补偿）
     */
    private void sendOrderSync(SeckillOrderDTO order, String tradeNo) {
        try {
            SeckillOrderSyncEvent event = new SeckillOrderSyncEvent(
                    order.getOrderNo(), order.getUserId(), order.getTotalAmount(),
                    order.getPayAmount(), LocalDateTime.now(), tradeNo);
            Message<String> message = BaggageUtils.buildMessage(objectMapper.writeValueAsString(event));
            rocketMQTemplate.syncSend(MqTopic.ORDER_SYNC, message);
        } catch (Exception e) {
            log.error("补记支付后同步消息发送失败: orderNo={}", order.getOrderNo(), e);
        }
    }
}
