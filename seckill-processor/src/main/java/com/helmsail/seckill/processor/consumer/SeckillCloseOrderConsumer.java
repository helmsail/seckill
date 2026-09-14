package com.helmsail.seckill.processor.consumer;

import com.helmsail.seckill.base.mq.MqGroup;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderDubboService;
import com.helmsail.seckill.base.order.SeckillOrderStatus;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import com.helmsail.seckill.processor.seckill.PurchaseLimitService;
import com.helmsail.seckill.processor.seckill.StockService;
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
 * 接收延迟消息（含 orderTimeoutJob 补发的立即消息）：待支付订单超时未付则关闭，并回补秒杀域库存与限购额度。
 * 主流程（processClose）：查询校验 → 条件关单 → 回补资源，每步跳过路径即 ACK。
 * 异常语义：订单不存在/状态非待支付属永久跳过；其余异常上抛触发 MQ 重投（拿不准就不关，方向安全）；
 * 回补失败自动登记持久化补偿（compensationJob 重试，超限转人工）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqTopic.SECKILL_CLOSE_ORDER, consumerGroup = MqGroup.SECKILL_CLOSE_ORDER_CONSUMER)
public class SeckillCloseOrderConsumer implements RocketMQListener<MessageExt> {

    @DubboReference
    private SeckillOrderDubboService seckillOrderService;

    private final StockService stockService;
    private final PurchaseLimitService purchaseLimitService;

    @Override
    public void onMessage(MessageExt message) {
        BaggageUtils.restore(message.getProperties());
        try {
            String orderNo = new String(message.getBody(), StandardCharsets.UTF_8);
            log.info("收到自动关单消息: orderNo={}", orderNo);
            processClose(orderNo);
        } finally {
            BaggageUtils.clear();
        }
    }

    /**
     * 关单主流程：查询校验 → 条件关单 → 回补资源
     *
     * 跳过路径直接返回（ACK）；意外异常上抛触发重投（拿不准就不关，方向安全）。
     */
    private void processClose(String orderNo) {
        SeckillOrderDTO order = loadPendingOrder(orderNo);
        if (order == null) {
            return;
        }

        // 条件关单：仅 PENDING 可关闭；false = 并发已处理（重复消息/并发支付），由赢家负责回补
        if (!seckillOrderService.closeOrder(orderNo)) {
            log.info("关单跳过（并发已处理）: orderNo={}", orderNo);
            return;
        }

        // 回补权限由条件关单唯一发放：重复消息/并发副本只有一个赢家回补
        restoreResources(order);

        log.info("订单已关闭并回补资源: orderNo={}, activityNo={}, skuNo={}, quantity={}",
                orderNo, order.getActivityNo(), order.getSkuNo(), order.getQuantity());
    }

    /**
     * 查询并校验可关性：订单不存在（永久错误）/ 状态非待支付（幂等闸）返回 null
     */
    private SeckillOrderDTO loadPendingOrder(String orderNo) {
        SeckillOrderDTO order;
        try {
            order = seckillOrderService.getByOrderNo(orderNo);
        } catch (BizException e) {
            // 订单不存在属永久错误，不重试
            log.warn("关单跳过，订单不存在: orderNo={}", orderNo);
            return null;
        }
        if (order.getOrderStatus() != SeckillOrderStatus.PENDING) {
            log.info("关单跳过，订单状态非待支付: orderNo={}, status={}", orderNo, order.getOrderStatus());
            return null;
        }
        return order;
    }

    /**
     * 回补秒杀域资源（restore 为标记守卫的幂等操作；失败自动登记持久化补偿，由 compensationJob 重试）
     */
    private void restoreResources(SeckillOrderDTO order) {
        try {
            stockService.restore(order.getActivityNo(), order.getSkuNo(), order.getQuantity(), order.getTraceId());
        } catch (Exception e) {
            log.error("关单回补库存失败，需人工核对: orderNo={}, activityNo={}, skuNo={}, quantity={}",
                    order.getOrderNo(), order.getActivityNo(), order.getSkuNo(), order.getQuantity(), e);
        }
        try {
            purchaseLimitService.restore(order.getActivityNo(), order.getSkuNo(),
                    String.valueOf(order.getUserId()), order.getQuantity(), order.getTraceId());
        } catch (Exception e) {
            log.error("关单回补限购失败，需人工核对: orderNo={}, activityNo={}, skuNo={}, quantity={}",
                    order.getOrderNo(), order.getActivityNo(), order.getSkuNo(), order.getQuantity(), e);
        }
    }
}
