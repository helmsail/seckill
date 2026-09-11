package com.helmsail.seckill.processor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.CreateSeckillOrderRequest;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import com.helmsail.seckill.processor.seckill.PurchaseLimitService;
import com.helmsail.seckill.processor.seckill.SeckillIdempotentService;
import com.helmsail.seckill.processor.seckill.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.SECKILL_ORDER,
        consumerGroup = "seckill-order-consumer-group",
        consumeMode = ConsumeMode.ORDERLY
)
public class SeckillOrderConsumer implements RocketMQListener<MessageExt> {

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    @DubboReference
    private SeckillOrderService seckillOrderService;

    private final SeckillIdempotentService idempotentService;
    private final StockService stockService;
    private final PurchaseLimitService purchaseLimitService;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;

    private static final int CLOSE_ORDER_DELAY_LEVEL = 14; // 10分钟

    @Override
    public void onMessage(MessageExt message) {
        BaggageUtils.restore(message.getProperties());
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            SeckillRequest request = parseRequest(json);
            if (request == null) return;

            String idempotentKey = buildIdempotentKey(request);
            if (!idempotentService.tryProcess(idempotentKey)) return;

            try {
                processSeckill(request, idempotentKey);
            } catch (Exception e) {
                log.error("秒杀处理异常: key={}", idempotentKey, e);
                idempotentService.markFailed(idempotentKey, "系统异常");
            }
        } finally {
            BaggageUtils.clear();
        }
    }

    private void processSeckill(SeckillRequest request, String idempotentKey) {
        SeckillProductSkuDTO sku = getSkuInfo(request);
        if (sku == null) {
            idempotentService.markFailed(idempotentKey, "SKU不存在");
            return;
        }

        // DB 权威状态终判（Redis 名单滞后时的兜底裁定点）
        if (sku.getShelfStatus() == null || sku.getShelfStatus() != 1) {
            idempotentService.markFailed(idempotentKey, "商品已下架");
            return;
        }

        String userId = request.getUserId();
        if (!purchaseLimitService.deduct(request.getActivityNo(), request.getSkuNo(),
                userId, sku.getPurchaseLimit(), request.getQuantity())) {
            idempotentService.markFailed(idempotentKey, "超过限购");
            return;
        }

        if (!stockService.deduct(request.getActivityNo(), request.getSkuNo(), request.getQuantity())) {
            purchaseLimitService.restore(request.getActivityNo(), request.getSkuNo(),
                    userId, request.getQuantity());
            idempotentService.markFailed(idempotentKey, "库存不足");
            return;
        }

        String orderNo = createOrder(request, sku);
        if (orderNo == null) {
            rollbackStockAndLimit(request, userId);
            idempotentService.markFailed(idempotentKey, "创建订单失败");
            return;
        }

        if (!sendCloseOrderMessage(orderNo)) {
            rollbackStockAndLimit(request, userId);
            idempotentService.markFailed(idempotentKey, "发送延迟消息失败");
            return;
        }

        idempotentService.markSuccess(idempotentKey, orderNo);
    }

    private SeckillRequest parseRequest(String json) {
        try {
            return objectMapper.readValue(json, SeckillRequest.class);
        } catch (Exception e) {
            log.error("消息反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private String buildIdempotentKey(SeckillRequest request) {
        return request.getActivityNo() + ":" + request.getSkuNo();
    }

    private SeckillProductSkuDTO getSkuInfo(SeckillRequest request) {
        return seckillProductSkuService.getByActivityNoAndSkuNo(request.getActivityNo(), request.getSkuNo());
    }

    private String createOrder(SeckillRequest request, SeckillProductSkuDTO sku) {
        try {
            CreateSeckillOrderRequest orderRequest = new CreateSeckillOrderRequest();
            orderRequest.setUserId(Long.parseLong(request.getUserId()));
            orderRequest.setTotalAmount(sku.getOriginalPrice());
            orderRequest.setPayAmount(sku.getSeckillPrice());
            return seckillOrderService.createOrder(orderRequest);
        } catch (Exception e) {
            log.error("创建订单失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean sendCloseOrderMessage(String orderNo) {
        try {
            Message<String> message = BaggageUtils.buildMessage(orderNo);
            rocketMQTemplate.syncSend(MqTopic.SECKILL_CLOSE_ORDER, message, CLOSE_ORDER_DELAY_LEVEL);
            return true;
        } catch (Exception e) {
            log.error("发送延迟消息失败: orderNo={}", orderNo, e);
            return false;
        }
    }

    private void rollbackStockAndLimit(SeckillRequest request, String userId) {
        stockService.restore(request.getActivityNo(), request.getSkuNo(), request.getQuantity());
        purchaseLimitService.restore(request.getActivityNo(), request.getSkuNo(),
                userId, request.getQuantity());
    }
}
