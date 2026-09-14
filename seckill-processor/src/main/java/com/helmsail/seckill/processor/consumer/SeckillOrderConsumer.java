package com.helmsail.seckill.processor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityDubboService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.mq.MqGroup;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.CreateSeckillOrderRequest;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderDubboService;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDubboService;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.base.seckill.SeckillResultStatus;
import com.helmsail.seckill.base.seckill.SeckillResultVO;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import com.helmsail.seckill.processor.seckill.PurchaseLimitService;
import com.helmsail.seckill.processor.seckill.SeckillConsumeStateService;
import com.helmsail.seckill.processor.seckill.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * 秒杀下单消费者（RocketMQ 并发消费）
 *
 * 消费语义（对齐主流：至少一次消费 + 每步幂等可重放）：
 *   - 闸门只拦终态：结果键为 SUCCESS/FAILED 时重投直接屏蔽；键为 PROCESSING 时查订单表裁定
 *     ——有单补写终态，无单判定为崩溃残留、接管重放（见 passGate）；
 *   - 扣减幂等：限购/库存扣减与 traceId 标记同脚本原子写入，重放跳过已扣步骤；
 *   - 业务失败（活动状态/限购/库存/下架）→ FAILED 终态，不重投（重试徒劳）；
 *   - 技术异常 → 抛出触发 MQ 重投（≤3 次）；重投耗尽按标记回补已扣资源并判 FAILED。
 * 幂等链：终态闸门（重投去重）→ 幂等扣减标记（重放安全）→ 唯一约束 uk_user_trace（落库去重终局权威）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.SECKILL_ORDER,
        consumerGroup = MqGroup.SECKILL_ORDER_CONSUMER
)
public class SeckillOrderConsumer implements RocketMQListener<MessageExt> {

    @DubboReference
    private SeckillProductSkuDubboService seckillProductSkuService;

    @DubboReference
    private SeckillOrderDubboService seckillOrderService;

    @DubboReference
    private ActivityDubboService activityService;

    private final SeckillConsumeStateService consumeStateService;
    private final StockService stockService;
    private final PurchaseLimitService purchaseLimitService;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;

    /** 关单延迟级别：默认延迟级别表第 14 级 = 10 分钟（4.x broker 定时消息以延迟级别实现） */
    private static final int CLOSE_ORDER_DELAY_LEVEL = 14;

    /** 发送超时（毫秒） */
    private static final long SEND_TIMEOUT_MS = 3000;

    /** 技术异常重投上限：收到重投消息时 getReconsumeTimes() 达到该值即转终局处理 */
    private static final int MAX_RECONSUME_TIMES = 3;

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

            try {
                if (!passGate(request, idempotentKey)) {
                    return;
                }
                // 兜底查证：结果键过期（>24h）或 Redis 数据丢失等极端重放时，
                // 以订单表为准短路，避免重新扣减（正常路径为一次分片索引查询）
                SeckillOrderDTO existing = findOrderByTraceId(request);
                if (existing != null) {
                    log.warn("订单已存在，重放短路: traceId={}, orderNo={}", idempotentKey, existing.getOrderNo());
                    consumeStateService.markSuccess(idempotentKey, existing.getOrderNo());
                    return;
                }

                processSeckill(request, idempotentKey);
            } catch (Exception e) {
                onConsumeException(message, request, idempotentKey, e);
            }
        } finally {
            BaggageUtils.clear();
        }
    }

    private void processSeckill(SeckillRequest request, String idempotentKey) {
        // 活动级 DB 状态终判：消息排队期间活动可能恰好关闭；无需判时间——消息只会产生于开始之后
        ActivityDTO activity = activityService.getByActivityNo(request.getActivityNo());
        if (activity == null || activity.getActivityStatus() != ActivityStatus.ACTIVE) {
            consumeStateService.markFailed(idempotentKey, "活动不在进行中");
            return;
        }

        SeckillProductSkuDTO sku = getSkuInfo(request);
        if (sku == null) {
            consumeStateService.markFailed(idempotentKey, "SKU不存在");
            return;
        }

        // DB 权威状态终判（Redis 名单滞后时的兜底裁定点）
        if (sku.getShelfStatus() == null || sku.getShelfStatus() != 1) {
            consumeStateService.markFailed(idempotentKey, "商品已下架");
            return;
        }

        String userId = request.getUserId();
        String activityNo = request.getActivityNo();
        String skuNo = request.getSkuNo();
        int quantity = request.getQuantity();

        // 扣减与 traceId 标记同脚本原子完成：重投重放时跳过已扣步骤（返回"已扣"视为成功）
        boolean limitPassed = purchaseLimitService.deduct(activityNo, skuNo, userId,
                sku.getPurchaseLimit(), quantity, idempotentKey);
        if (!limitPassed) {
            consumeStateService.markFailed(idempotentKey, "超过限购");
            return;
        }

        boolean stockPassed = stockService.deduct(activityNo, skuNo, quantity, idempotentKey);
        if (!stockPassed) {
            // 回补限购（标记守卫幂等）；回补失败仅记日志，业务失败语义不变
            purchaseLimitService.restore(activityNo, skuNo, userId, quantity, idempotentKey);
            consumeStateService.markFailed(idempotentKey, "库存不足");
            return;
        }

        String orderNo = createOrder(request, sku);
        if (orderNo == null) {
            // createOrder 异常/超时可能“实际已建单但回执丢失”：回补前查证，
            // 已建则按成功处理（补发延迟关单消息），避免双重回补造成账目不一致
            SeckillOrderDTO existing = findOrderByTraceId(request);
            if (existing == null) {
                rollbackByMarkers(request, idempotentKey);
                consumeStateService.markFailed(idempotentKey, "创建订单失败");
                return;
            }
            log.warn("createOrder 响应异常但订单已建，按成功处理: traceId={}, orderNo={}",
                    idempotentKey, existing.getOrderNo());
            orderNo = existing.getOrderNo();
        }

        if (!sendCloseOrderMessage(orderNo)) {
            // 订单已创建，不回补资源（订单生命周期仍成立）；
            // 延迟消息缺失由 closeOrderResendJob 扫描补发关单消息兜底
            log.error("发送延迟消息失败，依赖 closeOrderResendJob 兜底补关单: orderNo={}", orderNo);
        }

        consumeStateService.markSuccess(idempotentKey, orderNo);
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
            // 金额 = 单价 × 数量：与库存/限购按数量扣减对齐（折扣已在配置时算入单价，两位小数 × 整数不引入精度损失）
            BigDecimal quantityFactor = BigDecimal.valueOf(request.getQuantity());
            orderRequest.setTotalAmount(sku.getOriginalPrice().multiply(quantityFactor));
            orderRequest.setPayAmount(sku.getSeckillPrice().multiply(quantityFactor));
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

    /**
     * 消费闸门决策（重投安全核心）
     *
     * SETNX 抢到占位 → 放行处理；未抢到 → 读取结果键裁定：
     *   - 终态（success/failed）→ 屏蔽（重投去重，正常 ACK）；
     *   - 处理中（或读取为 null 的过期窗口）→ 查订单表：有单补写终态后屏蔽；
     *     无单判定为消费者崩溃残留，续期占位后接管重放（重放安全由幂等标记与唯一约束保证）。
     *
     * @return true 表示继续处理
     */
    private boolean passGate(SeckillRequest request, String traceId) {
        if (consumeStateService.tryProcess(traceId)) {
            return true;
        }
        SeckillResultVO current = consumeStateService.getResult(traceId);
        if (current != null && !SeckillResultStatus.PROCESSING.equals(current.getStatus())) {
            log.info("秒杀结果已终结，重投屏蔽: traceId={}, status={}", traceId, current.getStatus());
            return false;
        }
        SeckillOrderDTO existing = findOrderByTraceId(request);
        if (existing != null) {
            log.warn("占位处理中但订单已存在，补写终态: traceId={}, orderNo={}", traceId, existing.getOrderNo());
            consumeStateService.markSuccess(traceId, existing.getOrderNo());
            return false;
        }
        log.warn("处理中占位且无订单，判定崩溃残留，接管重放: traceId={}", traceId);
        consumeStateService.renewProcessing(traceId);
        return true;
    }

    /**
     * 消费异常处理：未达重投上限 → 抛出触发 MQ 重投（重放安全由幂等标记 + uk_user_trace 保证）；
     * 重投耗尽 → 终局：有订单补写成功终态；无订单按幂等标记回补已扣资源后判定失败。
     */
    private void onConsumeException(MessageExt message, SeckillRequest request, String traceId, Exception e) {
        if (message.getReconsumeTimes() < MAX_RECONSUME_TIMES) {
            log.error("秒杀处理技术异常，触发重投({}/{}): traceId={}",
                    message.getReconsumeTimes() + 1, MAX_RECONSUME_TIMES, traceId, e);
            // 抛出触发重投；非运行时异常包装上抛（broker 对两者一致按消费失败重投）
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(e);
        }
        log.error("秒杀处理异常且重投耗尽({}次)，转终局处理: traceId={}", message.getReconsumeTimes(), traceId, e);
        try {
            SeckillOrderDTO existing = findOrderByTraceId(request);
            if (existing != null) {
                // 订单已建、终态写回失败：补写成功；资源归属订单生命周期，不回补
                consumeStateService.markSuccess(traceId, existing.getOrderNo());
                return;
            }
            rollbackByMarkers(request, traceId);
            consumeStateService.markFailed(traceId, "系统异常，请重新发起");
        } catch (Exception terminalEx) {
            // 终局处理自身异常（如 DB 不可用无法查证归属）：仅日志留痕，结果键保持处理中待人工核对
            log.error("终局处理失败: traceId={}", traceId, terminalEx);
        }
    }

    /** 标记守卫回补：仅回补“确定已扣”（标记存在）的资源，宁可少还、不超还；回补失败仅记日志（需人工核对） */
    private void rollbackByMarkers(SeckillRequest request, String traceId) {
        stockService.restore(request.getActivityNo(), request.getSkuNo(), request.getQuantity(), traceId);
        purchaseLimitService.restore(request.getActivityNo(), request.getSkuNo(),
                request.getUserId(), request.getQuantity(), traceId);
    }

    /** 按 traceId 查订单（带 userId 路由分片）；查询异常上抛由上层统一兜底 */
    private SeckillOrderDTO findOrderByTraceId(SeckillRequest request) {
        return seckillOrderService.getByTraceId(
                Long.parseLong(request.getUserId()), request.getTraceId());
    }
}
