package com.helmsail.seckill.processor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityService;
import com.helmsail.seckill.base.activity.ActivityWindows;
import com.helmsail.seckill.base.mq.MqGroup;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.CreateSeckillOrderRequest;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.common.redis.RedisService;
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
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀下单消费者（RocketMQ 顺序消费）
 *
 * 失败语义（有意设计，不依赖 MQ 重投）：
 *   - 业务失败（限购/库存/窗口/下架）→ 结果键标记 FAILED（重试徒劳）；
 *   - 技术异常 → 同样标记 FAILED 且不重投（秒杀语义“宁可失败不乱账”），
 *     用户重新发起即全新 traceId，天然安全；异常详情另落档（seckill:fail:system:{traceId}）供排查/对账。
 * 幂等链：Redis 结果键（重投拦截）→ traceId 订单查证（兜底短路/回补纠正）→ 唯一约束 uk_user_trace（落库去重）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.SECKILL_ORDER,
        consumerGroup = MqGroup.SECKILL_ORDER_CONSUMER,
        consumeMode = ConsumeMode.ORDERLY
)
public class SeckillOrderConsumer implements RocketMQListener<MessageExt> {

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    @DubboReference
    private SeckillOrderService seckillOrderService;

    @DubboReference
    private ActivityService activityService;

    private final SeckillIdempotentService idempotentService;
    private final RedisService redisService;
    private final StockService stockService;
    private final PurchaseLimitService purchaseLimitService;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;

    /** 关单延迟级别：默认延迟级别表第 14 级 = 10 分钟（4.x broker 定时消息以延迟级别实现） */
    private static final int CLOSE_ORDER_DELAY_LEVEL = 14;

    /** 发送超时（毫秒） */
    private static final long SEND_TIMEOUT_MS = 3000;

    /** 系统异常落档保留天数（结果键仅 5 分钟，落久档供排查/对账） */
    private static final int FAIL_RECORD_TTL_DAYS = 30;

    @Override
    public void onMessage(MessageExt message) {
        BaggageUtils.restore(message.getProperties());
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            SeckillRequest request = parseRequest(json);
            if (request == null) return;

            String idempotentKey = buildIdempotentKey(request);
            if (idempotentKey == null || idempotentKey.isBlank()) {
                log.error("秒杀消息缺少 traceId，丢弃: {}", json);
                return;
            }
            if (!idempotentService.tryProcess(idempotentKey)) return;

            try {
                // 兜底查证：结果键过期（>24h）或 Redis 数据丢失等极端重放时，
                // 以订单表为准短路，避免重新扣减（正常路径为一次分片索引查询）
                SeckillOrderDTO existing = findOrderByTraceId(request);
                if (existing != null) {
                    log.warn("订单已存在，重放短路: traceId={}, orderNo={}", idempotentKey, existing.getOrderNo());
                    idempotentService.markSuccess(idempotentKey, existing.getOrderNo());
                    return;
                }

                processSeckill(request, idempotentKey);
            } catch (Exception e) {
                // 设计取舍：技术异常不重投（宁可失败不乱账）——用户重新发起为新 traceId，天然安全；
                // 异常详情落档 30 天（结果键仅存 5 分钟），供排查与后续对账比对
                log.error("秒杀处理异常: key={}", idempotentKey, e);
                idempotentService.markFailed(idempotentKey, "系统异常，请重新发起");
                recordSystemFailure(request, e);
            }
        } finally {
            BaggageUtils.clear();
        }
    }

    private void processSeckill(SeckillRequest request, String idempotentKey) {
        // 活动级 DB 权威终判（缓存状态同步延迟窗口内的最后一道闸）
        ActivityDTO activity = activityService.getByActivityNo(request.getActivityNo());
        if (!ActivityWindows.isInEffectiveWindow(activity, LocalDateTime.now())) {
            idempotentService.markFailed(idempotentKey, "活动不在生效时段");
            return;
        }

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
        // 扣减成功标记：异常路径仅回补“确定已扣”的资源（宁可少还，不超还）
        boolean limitDeducted = false;
        boolean stockDeducted = false;
        try {
            limitDeducted = purchaseLimitService.deduct(request.getActivityNo(), request.getSkuNo(),
                    userId, sku.getPurchaseLimit(), request.getQuantity());
            if (!limitDeducted) {
                idempotentService.markFailed(idempotentKey, "超过限购");
                return;
            }

            stockDeducted = stockService.deduct(request.getActivityNo(), request.getSkuNo(), request.getQuantity());
            if (!stockDeducted) {
                purchaseLimitService.restore(request.getActivityNo(), request.getSkuNo(),
                        userId, request.getQuantity());
                idempotentService.markFailed(idempotentKey, "库存不足");
                return;
            }

            String orderNo = createOrder(request, sku);
            if (orderNo == null) {
                // createOrder 异常/超时可能“实际已建单但回执丢失”：回补前查证，
                // 已建则按成功处理（补发延迟关单消息），避免双重回补造成账目不一致
                SeckillOrderDTO existing = findOrderByTraceId(request);
                if (existing == null) {
                    rollbackStockAndLimit(request, userId);
                    idempotentService.markFailed(idempotentKey, "创建订单失败");
                    return;
                }
                log.warn("createOrder 响应异常但订单已建，按成功处理: traceId={}, orderNo={}",
                        idempotentKey, existing.getOrderNo());
                orderNo = existing.getOrderNo();
            }

            if (!sendCloseOrderMessage(orderNo)) {
                // 订单已创建，不回补资源（订单生命周期仍成立）；
                // 延迟消息缺失由 orderTimeoutJob 扫描补发关单消息兜底
                log.error("发送延迟消息失败，依赖 orderTimeoutJob 兜底补关单: orderNo={}", orderNo);
            }

            idempotentService.markSuccess(idempotentKey, orderNo);
        } catch (Exception e) {
            // 异常补偿：仅回补已明确成功的扣减（restore 自身幂等）
            if (stockDeducted) {
                stockService.restore(request.getActivityNo(), request.getSkuNo(), request.getQuantity());
            }
            if (limitDeducted) {
                purchaseLimitService.restore(request.getActivityNo(), request.getSkuNo(),
                        userId, request.getQuantity());
            }
            throw e;
        }
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
        // 幂等与结果定位统一使用请求级唯一键 traceId
        return request.getTraceId();
    }

    private SeckillProductSkuDTO getSkuInfo(SeckillRequest request) {
        return seckillProductSkuService.getByActivityNoAndSkuNo(request.getActivityNo(), request.getSkuNo());
    }

    private String createOrder(SeckillRequest request, SeckillProductSkuDTO sku) {
        try {
            CreateSeckillOrderRequest orderRequest = new CreateSeckillOrderRequest();
            orderRequest.setUserId(Long.parseLong(request.getUserId()));
            orderRequest.setActivityNo(request.getActivityNo());
            orderRequest.setSkuNo(request.getSkuNo());
            orderRequest.setQuantity(request.getQuantity());
            orderRequest.setTotalAmount(sku.getOriginalPrice());
            orderRequest.setPayAmount(sku.getSeckillPrice());
            orderRequest.setTraceId(request.getTraceId());
            return seckillOrderService.createOrder(orderRequest);
        } catch (Exception e) {
            log.error("创建订单失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean sendCloseOrderMessage(String orderNo) {
        try {
            Message<String> message = BaggageUtils.buildMessage(orderNo);
            // 延迟发送注意两点：
            //   1) 三参 syncSend 的第三参是“超时毫秒”而非延迟级别，误传级别会导致假超时且消息立即投递；
            //   2) syncSendDelayTimeSeconds 依赖 5.x 客户端定时消息（Message.setDelayTimeSec），4.x broker 不支持会立即投递；
            //      本项目 broker 为 4.9.7，必须用四参重载（第三参=超时，第四参=延迟级别）
            rocketMQTemplate.syncSend(MqTopic.SECKILL_CLOSE_ORDER, message, SEND_TIMEOUT_MS, CLOSE_ORDER_DELAY_LEVEL);
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

    /** 按 traceId 查订单（带 userId 路由分片）；查询异常上抛由上层统一兜底 */
    private SeckillOrderDTO findOrderByTraceId(SeckillRequest request) {
        return seckillOrderService.getByTraceId(
                Long.parseLong(request.getUserId()), request.getTraceId());
    }

    /** 系统异常落档（30 天）：保留失败上下文供排查/对账，落档失败不影响主流程 */
    private void recordSystemFailure(SeckillRequest request, Exception e) {
        try {
            String detail = "userId=" + request.getUserId()
                    + ", activityNo=" + request.getActivityNo()
                    + ", skuNo=" + request.getSkuNo()
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage();
            redisService.set(String.format(SeckillRedisKey.KEY_SECKILL_FAIL_SYSTEM, request.getTraceId()),
                    detail, FAIL_RECORD_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception ex) {
            log.error("系统异常落档失败: traceId={}", request.getTraceId(), ex);
        }
    }
}
