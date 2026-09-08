package com.helmsail.seckill.processor.consumer;

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
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "seckill-order-topic", consumerGroup = "seckill-order-consumer-group")
public class SeckillOrderConsumer implements RocketMQListener<SeckillRequest> {

    @DubboReference
    private SeckillSkuService seckillSkuService;

    @DubboReference
    private SeckillOrderService seckillOrderService;

    private final SeckillIdempotentService idempotentService;
    private final StockService stockService;
    private final PurchaseLimitService purchaseLimitService;

    @Override
    public void onMessage(SeckillRequest request) {
        String traceId = request.getActivityNo() + ":" + request.getSkuNo();

        if (!idempotentService.tryProcess(traceId)) {
            return;
        }

        try {
            SeckillSkuDTO sku = getSkuInfo(request);
            if (sku == null) {
                idempotentService.markFailed(traceId, "SKU不存在");
                return;
            }

            String userId = request.getUserId();
            if (!purchaseLimitService.deduct(request.getActivityNo(), request.getSkuNo(),
                    userId, sku.getPurchaseLimit(), request.getQuantity())) {
                idempotentService.markFailed(traceId, "超过限购");
                return;
            }

            if (!stockService.deduct(request.getSkuNo(), request.getQuantity())) {
                purchaseLimitService.restore(request.getActivityNo(), request.getSkuNo(),
                        userId, request.getQuantity());
                idempotentService.markFailed(traceId, "库存不足");
                return;
            }

            String orderNo = createOrder(request, sku);
            if (orderNo == null) {
                stockService.restore(request.getSkuNo(), request.getQuantity());
                purchaseLimitService.restore(request.getActivityNo(), request.getSkuNo(),
                        userId, request.getQuantity());
                idempotentService.markFailed(traceId, "创建订单失败");
                return;
            }

            idempotentService.markSuccess(traceId, orderNo);

        } catch (Exception e) {
            log.error("秒杀处理异常: traceId={}", traceId, e);
            idempotentService.markFailed(traceId, "系统异常");
        }
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
}
