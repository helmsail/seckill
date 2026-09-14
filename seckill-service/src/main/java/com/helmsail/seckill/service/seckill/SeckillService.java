package com.helmsail.seckill.service.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.base.seckill.SeckillResultStatus;
import com.helmsail.seckill.base.seckill.SeckillResultVO;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.common.tracing.BaggageKeys;
import com.helmsail.seckill.common.tracing.UserContext;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀编排服务
 *
 * 准入检查全部委托 CheckService（逐项失败即抛对应业务码），
 * 通过后发送 MQ 异步下单；结果由 processor 写回结果键，C 端轮询获取；
 * 发送异常时本服务兜底写 FAILED（setIfAbsent 不覆盖消费端状态），保证轮询必收敛。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    /** 终态结果保留时长（秒）：与 processor 侧 FINAL_TTL_SECONDS 齐平 */
    private static final int FINAL_TTL_SECONDS = 24 * 60 * 60;

    private final CheckService checkService;
    private final RedisService redisService;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public String executeSeckill(SeckillRequest request) {
        String userId = UserContext.currentUserId();
        String activityNo = request.getActivityNo();
        String skuNo = request.getSkuNo();
        String traceId = MDC.get(BaggageKeys.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            // traceId 由网关生成并透传，缺失即链路断裂：快速失败暴露，不做静默兜底
            // （traceId 兼任幂等键与结果键，兜底会掩盖透传故障并割裂全链路日志）
            throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(), "请求缺失链路标识 traceId");
        }

        // 六项准入检查按序收缩，任一失败即在 CheckService 内抛对应业务码
        checkService.checkRateLimit(userId);
        checkService.checkActivity(activityNo, userId);
        checkService.checkBlacklist(userId);
        checkService.checkPurchaseLimit(activityNo, skuNo, userId, request.getQuantity());
        checkService.checkSkuOnShelf(activityNo, skuNo);
        checkService.checkStock(activityNo, skuNo, request.getQuantity());

        request.setUserId(userId);
        request.setTraceId(traceId);

        // 同步发送（send 即同步）。发送异常/超时只能说明"结果不确定"：客户端已内置重试（默认 2 次），
        // 消息可能已实际到达 broker；此时向用户返回失败会诱导重试——重试请求携带新 traceId，
        // 绕过消费端按 traceId 的幂等链，造成真正重复的下单尝试。
        // 故发送异常只记日志、仍返回 traceId，并兜底写 FAILED 结果键保证轮询必收敛（见 fallbackOnSendFailure）。
        // SendStatus 不做显式判定：本集群 ASYNC_FLUSH + 单 master，FLUSH_DISK_TIMEOUT/SLAVE_* 实际不可达，
        // 且消息只要入 broker 存储即会投递，判定结果已无动作可做，回归默认行为。
        try {
            String json = objectMapper.writeValueAsString(request);
            Message<String> message = BaggageUtils.buildMessage(json);
            rocketMQTemplate.send(MqTopic.SECKILL_ORDER, message);
        } catch (Exception e) {
            log.error("秒杀消息发送结果不确定（消息可能已投递）: traceId={}", traceId, e);
            fallbackOnSendFailure(traceId);
        }

        log.info("秒杀请求已提交: userId={}, activityNo={}, skuNo={}, traceId={}", userId, activityNo, skuNo, traceId);
        return traceId;
    }

    /**
     * 发送异常兜底：best-effort 写 FAILED 结果键
     *
     * setIfAbsent 不覆盖已存在状态：消费者已占位（PROCESSING）或已写终态时本次写入无效果，
     * 已被消费/正在消费的场景不受影响；消息彻底丢失（未达 broker）时用户轮询收敛为失败，按提示重新发起。
     * 边界：消息已送达但尚未被消费时，本兜底会抢先判负并屏蔽其消费——用户重发新 traceId 即可，
     * 不会产生重复建单（旧消息被终态闸门 ACK 丢弃）。
     */
    private void fallbackOnSendFailure(String traceId) {
        try {
            String resultKey = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
            SeckillResultVO vo = new SeckillResultVO(SeckillResultStatus.FAILED, null, "提交异常，请重新发起");
            redisService.setIfAbsent(resultKey, objectMapper.writeValueAsString(vo),
                    FINAL_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("发送异常兜底写结果键失败: traceId={}", traceId, e);
        }
    }

    public SeckillResultVO pollResult(String traceId) {
        String resultKey = String.format(SeckillRedisKey.KEY_SECKILL_RESULT, traceId);
        String json = redisService.get(resultKey);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SeckillResultVO.class);
        } catch (Exception e) {
            log.error("秒杀结果解析失败: traceId={}", traceId, e);
            return null;
        }
    }
}
