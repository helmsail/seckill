package com.helmsail.seckill.processor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqGroup;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import com.helmsail.seckill.processor.seckill.SeckillCheckService;
import com.helmsail.seckill.processor.seckill.SeckillOrderCreateService;
import com.helmsail.seckill.processor.seckill.SeckillResultService;
import com.helmsail.seckill.processor.seckill.SkuStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 秒杀下单消费者（RocketMQ 并发消费；本类全权编排——结果状态机 + 校验/限购/库存/订单/延迟消息）
 *
 * 编排链：标记处理中 → 二次校验（DB 权威）→ 单脚本原子扣减（活动限购+SKU限购+库存）→ 建单 → 延迟关单消息 → 终态收尾；
 * 明确失败即按序收敛：校验/扣减/建单失败 → 回补已扣层（标记守卫幂等）并判 FAILED；
 * 技术异常（不确定态，如超时/连接断）不重投：直接转终局裁定（有单补成功 / 无单回补判负），收敛失败仅告警留人工。
 *
 * 消费语义（对齐主流：至少一次消费 + 每步幂等可重放）：
 *   - 处理中标记以订单表为最高权威：有单即补写终态短路；无单看结果键——SUCCESS/FAILED 重复消费屏蔽，
 *     PROCESSING 判定崩溃残留、接管重放（见 markProcessing）；
 *   - 扣减幂等：限购/库存扣减与 traceId 标记同脚本原子写入，重放跳过已扣步骤；
 *   - 业务失败（活动状态/限购/库存/下架）→ FAILED 终态；
 *   - 技术异常（超时/连接断）不重投：客户端（Redis/MQ/DB 连接池）自身重试覆盖瞬时抖动，本类只做裁定收敛。
 * 幂等链：终态闸门（重复消费去重）→ 幂等扣减标记（重放安全）→ 唯一约束 uk_user_trace（落库去重终局权威）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqTopic.SECKILL_ORDER,
        consumerGroup = MqGroup.SECKILL_ORDER_CONSUMER,
        // 并发消费线程:v6 架构单实例 200 线程(替代 5 实例×40;8 队列由单实例统一消费,分配更均匀)。
        // 标定:40 线程(单条含 3 次串行 base 调用)≈2400/s → 200 线程 ≈1 万/s 级,覆盖 4000/s 目标。
        // 2.3.4 语义:consumeThreadNumber → setConsumeThreadMin;consumeThreadMax → setConsumeThreadMax(两者必须同设,
        // 否则核心池=20、队列无界,线程永不会扩到 max)
        consumeThreadNumber = 200,
        consumeThreadMax = 200
)
public class SeckillOrderConsumer implements RocketMQListener<MessageExt> {

    /** 关单延迟级别：默认延迟级别表第 14 级 = 10 分钟（4.x broker 定时消息以延迟级别实现） */
    private static final int CLOSE_ORDER_DELAY_LEVEL = 14;

    /** 发送超时（毫秒） */
    private static final long SEND_TIMEOUT_MS = 3000;

    private final SeckillResultService resultService;
    private final SeckillCheckService checkService;
    private final SkuStockService skuStockService;
    private final SeckillOrderCreateService orderCreateService;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt message) {
        BaggageUtils.restore(message.getProperties());
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            SeckillRequest request;
            try {
                request = objectMapper.readValue(json, SeckillRequest.class);
            } catch (Exception e) {
                log.error("消息反序列化失败: {}", e.getMessage());
                return;
            }
            String traceId = request.getTraceId();
            if (traceId == null || traceId.isBlank()) {
                log.error("秒杀消息缺少 traceId，丢弃: {}", json);
                return;
            }

            try {
                // ① 标记处理中：订单表权威裁定 + PROCESSING 记录（重投去重/崩溃接管）
                if (!resultService.markProcessing(request)) {
                    return;
                }

                // ② 二次校验：活动状态 + 上下架（DB 权威——运营操作可能在 MQ 传输期间发生变更）
                if (!checkService.check(request)) {
                    resultService.markFailed(traceId, "活动不在进行中或商品已下架");
                    return;
                }
                String userId = request.getUserId();
                String activityNo = request.getActivityNo();
                String skuNo = request.getSkuNo();
                int quantity = request.getQuantity();

                // ③ 扣减：活动限购 + SKU 限购 + 库存单脚本一口原子（任一层不足即整体拒绝，无中间态无需补偿）
                String deductFail = skuStockService.deduct(activityNo, skuNo, userId, quantity, traceId);
                if (deductFail != null) {
                    resultService.markFailed(traceId, deductFail);
                    return;
                }

                // ④ 建单（SKU 自取；含回执证伪）；失败回补已扣三层
                String orderNo = orderCreateService.createOrder(request);
                if (orderNo == null) {
                    // 回补已扣三层（标记守卫幂等）；回补失败仅记日志，业务失败语义不变
                    skuStockService.restore(activityNo, skuNo, userId, quantity, traceId);
                    resultService.markFailed(traceId, "创建订单失败");
                    return;
                }

                // ⑤ 延迟关单消息：失败仅日志（订单已建不回补资源，由 closeOrderResendJob 扫描兜底）
                //   注意：三参 syncSend 的第三参是“超时毫秒”而非延迟级别，误传级别会导致假超时且消息立即投递；
                //   syncSendDelayTimeSeconds 依赖 5.x 客户端定时消息，本项目 broker 4.9.7 必须用四参重载
                try {
                    Message<String> closeMessage = BaggageUtils.buildMessage(orderNo);
                    rocketMQTemplate.syncSend(MqTopic.SECKILL_CLOSE_ORDER, closeMessage,
                            SEND_TIMEOUT_MS, CLOSE_ORDER_DELAY_LEVEL);
                } catch (Exception e) {
                    log.error("发送延迟消息失败，依赖 closeOrderResendJob 兜底补关单: orderNo={}", orderNo, e);
                }

                // ⑥ 终态收尾
                resultService.markSuccess(traceId, orderNo);
            } catch (Exception e) {
                // 技术异常（不确定态，如超时/连接断）：不重投——先按订单表裁定，再收敛
                // （有单 → markProcessing 补写终态；无单 → 回补“确定已扣”并判负）
                log.error("秒杀处理技术异常，转终局收敛: traceId={}", traceId, e);
                try {
                    if (!resultService.markProcessing(request)) {
                        return;   // 有单已补终态 / 已终态 → 直接收敛，不回补
                    }
                    // 回补已扣三层（借据销账幂等，仅回补“确定已扣”）
                    skuStockService.restore(request.getActivityNo(), request.getSkuNo(),
                            request.getUserId(), request.getQuantity(), traceId);
                    resultService.markFailed(traceId, "系统异常，请重新发起");
                } catch (Exception terminalEx) {
                    // 收敛自身失败（如 DB 不可用无法查证归属）：告警留痕，结果键保持处理中待人工核对
                    log.error("终局收敛失败（需人工核对）: traceId={}", traceId, terminalEx);
                }
            }
        } finally {
            BaggageUtils.clear();
        }
    }
}
