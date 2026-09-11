package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.helmsail.seckill.base.order.SeckillOrderSyncEvent;
import com.helmsail.seckill.support.api.order.OrderService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 订单同步对账任务（兜底支付成功事件丢失导致的主域漏账）
 *
 * 以「主域存在性」为唯一判据：扫描时间窗内秒杀域已支付订单，
 * 对比主域缺失的订单补发同步消息（复用 processor 消费者 + support 幂等 create）。
 * 无需维护"已同步"状态：主域唯一键即同步台账，发送失败/消费失败/死信漏账统一捞回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncReconcileJobHandler {

    /** 对账时间窗（分钟）：需覆盖任务最长停摆时间，窗口外的漏账不再补 */
    private static final int WINDOW_MINUTES = 24 * 60;

    /** 单次扫描上限（分片表按分片生效，实际量最多为 分片数 × limit） */
    private static final int SCAN_LIMIT = 500;

    /** 主域批量存在性查询的单批大小 */
    private static final int QUERY_BATCH_SIZE = 500;

    @DubboReference
    private SeckillOrderService seckillOrderService;

    @DubboReference
    private OrderService supportOrderService;

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @XxlJob("orderSyncReconcileJob")
    public void execute() {
        List<SeckillOrderDTO> paidOrders = seckillOrderService.listPaidOrdersSince(WINDOW_MINUTES, SCAN_LIMIT);
        if (paidOrders.isEmpty()) {
            return;
        }
        List<String> orderNos = paidOrders.stream().map(SeckillOrderDTO::getOrderNo).toList();
        Set<String> existing = queryExistingOrderNos(orderNos);

        int resent = 0;
        for (SeckillOrderDTO order : paidOrders) {
            if (existing.contains(order.getOrderNo())) {
                continue;
            }
            try {
                SeckillOrderSyncEvent event = new SeckillOrderSyncEvent(
                        order.getOrderNo(), order.getUserId(), order.getTotalAmount(),
                        order.getPayAmount(), order.getPaidTime(), order.getTradeNo());
                rocketMQTemplate.syncSend(MqTopic.ORDER_SYNC, objectMapper.writeValueAsString(event));
                resent++;
                log.warn("订单同步漏账补发: orderNo={}", order.getOrderNo());
            } catch (Exception e) {
                log.error("订单同步漏账补发失败: orderNo={}", order.getOrderNo(), e);
            }
        }
        log.info("订单同步对账完成: 扫描={}, 缺失={}, 补发={}",
                paidOrders.size(), paidOrders.size() - existing.size(), resent);
    }

    /**
     * 分批查询主域已存在的订单号
     */
    private Set<String> queryExistingOrderNos(List<String> orderNos) {
        Set<String> existing = new HashSet<>();
        for (int i = 0; i < orderNos.size(); i += QUERY_BATCH_SIZE) {
            List<String> batch = orderNos.subList(i, Math.min(i + QUERY_BATCH_SIZE, orderNos.size()));
            existing.addAll(supportOrderService.listExistingOrderNos(batch));
        }
        return existing;
    }
}
