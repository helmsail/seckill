package com.helmsail.seckill.processor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.order.CreateSeckillOrderRequest;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.helmsail.seckill.base.sku.SeckillSkuDTO;
import com.helmsail.seckill.base.sku.SeckillSkuService;
import com.helmsail.seckill.common.request.SeckillRequest;
import com.helmsail.seckill.processor.seckill.PurchaseLimitService;
import com.helmsail.seckill.processor.seckill.SeckillIdempotentService;
import com.helmsail.seckill.processor.seckill.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "seckill-order-topic",
        consumerGroup = "seckill-order-consumer-group",
        consumeMode = ConsumeMode.ORDERLY
)
public class SeckillOrderConsumer implements RocketMQListener<String> {

    @DubboReference
    private SeckillSkuService seckillSkuService;

    @DubboReference
    private SeckillOrderService seckillOrderService;

    private final SeckillIdempotentService idempotentService;
    private final StockService stockService;
    private final PurchaseLimitService purchaseLimitService;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;

    private static final String CLOSE_ORDER_TOPIC = "seckill-close-order-topic";
    private static final int CLOSE_ORDER_DELAY_LEVEL = 14; // 10分钟

    @Override
    public void onMessage(String json) {
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
    }

    private void processSeckill(SeckillRequest request, String idempotentKey) {
        SeckillSkuDTO sku = getSkuInfo(request);
        if (sku == null) {
            idempotentService.markFailed(idempotentKey, "SKU不存在");
            return;
        }

        String userId = request.getUserId();
        if (!purchaseLimitService.deduct(request.getActivityNo(), request.getSkuNo(),
                userId, sku.getPurchaseLimit(), request.getQuantity())) {
            idempotentService.markFailed(idempotentKey, "超过限购");
            return;
        }

        if (!stockService.deduct(request.getSkuNo(), request.getQuantity())) {
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

    private SeckillSkuDTO getSkuInfo(SeckillRequest request) {
        List<SeckillSkuDTO> skus = seckillSkuService.listBySkProductId(request.getActivityNo());
        return skus.stream()
                .filter(sku -> sku.getSkuNo().equals(request.getSkuNo()))
                .findFirst()
                .orElse(null);
    }

    private String createOrder(SeckillRequest request, SeckillSkuDTO sku) {
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
            Message<String> message = MessageBuilder.withPayload(orderNo).build();
            rocketMQTemplate.syncSend(CLOSE_ORDER_TOPIC, message, CLOSE_ORDER_DELAY_LEVEL);
            return true;
        } catch (Exception e) {
            log.error("发送延迟消息失败: orderNo={}", orderNo, e);
            return false;
        }
    }

    private void rollbackStockAndLimit(SeckillRequest request, String userId) {
        stockService.restore(request.getSkuNo(), request.getQuantity());
        purchaseLimitService.restore(request.getActivityNo(), request.getSkuNo(),
                userId, request.getQuantity());
    }
}
