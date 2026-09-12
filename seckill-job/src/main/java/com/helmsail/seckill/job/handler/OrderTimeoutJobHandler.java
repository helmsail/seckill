package com.helmsail.seckill.job.handler;

import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单超时补偿任务（兜底延迟消息失效的漏网关单）
 *
 * 扫描创建超过 TIMEOUT_MINUTES 仍为待支付的订单，补发立即关单消息，
 * 复用关单消费者链路（关闭 + 库存/限购回补）。
 * 阈值需大于延迟消息时长（10 分钟），避免与正常链路重复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutJobHandler {

    /** 补关单阈值（分钟）：延迟消息时长（10 分钟）+ 1 分钟冗余，只补漏不误抢 */
    private static final int TIMEOUT_MINUTES = 11;

    /** 单次扫描上限（分片表按分片生效，实际量最多为 分片数 × limit） */
    private static final int BATCH_LIMIT = 100;

    @DubboReference
    private SeckillOrderService seckillOrderService;

    private final RocketMQTemplate rocketMQTemplate;

    @XxlJob("orderTimeoutJob")
    public void execute() {
        List<String> orderNos = seckillOrderService.listTimeoutOrderNos(TIMEOUT_MINUTES, BATCH_LIMIT);
        if (orderNos.isEmpty()) {
            return;
        }
        int sent = 0;
        for (String orderNo : orderNos) {
            try {
                rocketMQTemplate.syncSend(MqTopic.SECKILL_CLOSE_ORDER, orderNo);
                sent++;
            } catch (Exception e) {
                log.error("补发关单消息失败: orderNo={}", orderNo, e);
            }
        }
        log.info("订单超时补偿完成: 扫描={}, 补发={}", orderNos.size(), sent);
    }
}
