package com.helmsail.seckill.service.pay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderDubboService;
import com.helmsail.seckill.base.order.SeckillOrderStatus;
import com.helmsail.seckill.base.order.SeckillOrderSyncEvent;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.common.tracing.UserContext;
import com.helmsail.seckill.common.tracing.mq.BaggageUtils;
import com.helmsail.seckill.support.api.pay.PayChannelType;
import com.helmsail.seckill.support.api.pay.PayGatewayDubboService;
import com.helmsail.seckill.support.api.pay.PayNotifyResult;
import com.helmsail.seckill.support.api.pay.PayRequest;
import com.helmsail.seckill.support.api.pay.PayTradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.Method;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 收银台支付服务（两入口）
 *
 * prePay：待支付订单出二维码（渠道预创建 + 缓存）；
 * payCallback：渠道异步回调——验签核对后锁内完成支付（条件更新 + 清码 + 主域同步），是支付完成的唯一写路径。
 *
 * 订单状态查询见 order 包的 OrderQueryService（纯读，不感知渠道）。
 * 所有"转为 PAID"的核对与写入均在 KEY_PAY_LOCK 锁内完成（check-then-act 防竞态）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    /** 二维码缓存有效期（秒） */
    private static final int QR_CODE_CACHE_TTL = 10 * 60;

    /** 读写混合：paySuccess 写方法级禁重试保持写语义确定；读方法不声明，自动走引用级默认 */
    @DubboReference(methods = @Method(name = "paySuccess", retries = 0))
    private SeckillOrderDubboService seckillOrderService;

    /** 支付渠道网关（preCreate/verifyNotify 含渠道调用，禁自动重试） */
    @DubboReference(retries = 0)
    private PayGatewayDubboService payGatewayService;

    private final RedisService redisService;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 预支付：获取支付二维码
     *
     * 归属与状态校验先于缓存读取：缓存键不含 userId，命中直达会绕过校验。
     */
    public String prePay(String orderNo) {
        // 1. 查询订单（不存在由 base 抛 ORDER_NOT_FOUND）
        SeckillOrderDTO order = seckillOrderService.getByOrderNo(orderNo);

        // 2. 归属校验：仅订单本人可获取支付码
        //    必须先于缓存读取：二维码缓存键不含 userId，缓存命中直达返回会绕过归属校验（越权）
        if (!String.valueOf(order.getUserId()).equals(UserContext.currentUserId())) {
            throw new BizException(ResultEnum.FORBIDDEN);
        }

        // 3. 状态校验：仅待支付订单可发起支付（同理必须先于缓存，避免已支付/已关闭订单拿到旧码）
        if (order.getOrderStatus() != SeckillOrderStatus.PENDING) {
            throw new BizException(SeckillResultEnum.ORDER_STATUS_NOT_ALLOWED);
        }

        // 4. 检查缓存
        String cacheKey = String.format(SeckillRedisKey.KEY_PAY_QRCODE, orderNo);
        String cachedQrCode = redisService.get(cacheKey);
        if (cachedQrCode != null) {
            return cachedQrCode;
        }

        // 5. 调用支付渠道预创建
        PayRequest payRequest = new PayRequest();
        payRequest.setSubject("秒杀活动订单");
        payRequest.setOutTradeNo(order.getOrderNo());
        payRequest.setTotalAmount(String.valueOf(order.getPayAmount()));
        String qrCode = payGatewayService.preCreate(PayChannelType.MOCK, payRequest);

        // 6. 缓存二维码
        redisService.set(cacheKey, qrCode, QR_CODE_CACHE_TTL, TimeUnit.SECONDS);

        return qrCode;
    }

    /**
     * 支付回调（渠道异步通知原始参数）——支付完成唯一写路径
     *
     * Mock 场景：按 preCreate 返回的操作说明访问本接口即模拟支付成功；
     * 接入真实渠道时替换 PayChannel.verifyNotify 的真实验签即可。
     * 校验链：验签 → 交易状态过滤 → 加锁 → 订单核对（状态分流 + 金额）→ 转 PAID。
     */
    public void payCallback(Map<String, String> params) {
        // 1. 渠道验签 + 解析
        PayNotifyResult notify = payGatewayService.verifyNotify(PayChannelType.MOCK, params);
        if (notify == null || !notify.isValid()) {
            throw new BizException(ResultEnum.FORBIDDEN.getCode(), "支付回调验签失败");
        }
        if (!PayTradeStatus.PAID.equals(notify.getTradeStatus())) {
            log.warn("支付回调非成功状态，忽略: tradeStatus={}", notify.getTradeStatus());
            return;
        }
        String orderNo = notify.getOutTradeNo();
        if (orderNo == null || orderNo.isBlank()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "回调缺少商户订单号");
        }

        String lockKey = String.format(SeckillRedisKey.KEY_PAY_LOCK, orderNo);
        RLock lock = redissonClient.getLock(lockKey);
        try {
            lock.lock(10, TimeUnit.SECONDS);

            // 2. 订单核对：存在 + 待支付 + 金额一致
            SeckillOrderDTO order = seckillOrderService.getByOrderNo(orderNo);
            if (order.getOrderStatus() != SeckillOrderStatus.PENDING) {
                if (order.getOrderStatus() == SeckillOrderStatus.PAID) {
                    // 重复回调：订单已支付，幂等忽略
                    log.warn("重复支付回调，幂等忽略: orderNo={}", orderNo);
                    return;
                }
                // CLOSED：关单后渠道到账（回调延迟/关单误判）。生产环境应触发自动退款；
                // 本项目明确不做退款，降级为结构化告警 + 人工处理
                log.error("关单后收到支付成功回调，需人工处理（生产需自动退款）: orderNo={}, tradeNo={}, amount={}, notifyTime={}",
                        orderNo, notify.getTradeNo(), notify.getTotalAmount(), LocalDateTime.now());
                return;
            }
            // 金额核对：缺省跳过（兼容未回传金额的渠道）；格式非法/不一致均拒绝
            if (notify.getTotalAmount() != null) {
                BigDecimal notifyAmount;
                try {
                    notifyAmount = new BigDecimal(notify.getTotalAmount());
                } catch (NumberFormatException e) {
                    throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "回调金额格式非法");
                }
                if (notifyAmount.compareTo(order.getPayAmount()) != 0) {
                    throw new BizException(SeckillResultEnum.PAY_AMOUNT_MISMATCH);
                }
            }

            // 3. 支付完成：条件更新（重复/竞态幂等）→ 清二维码缓存 → 同步主域（发送失败不回滚支付，漏同步由对账任务捞回）
            seckillOrderService.paySuccess(order.getOrderNo(), notify.getTradeNo());
            redisService.delete(String.format(SeckillRedisKey.KEY_PAY_QRCODE, order.getOrderNo()));
            sendOrderSync(order, notify.getTradeNo());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 同步成功订单到主域（MQ）
     */
    private void sendOrderSync(SeckillOrderDTO order, String tradeNo) {
        try {
            SeckillOrderSyncEvent event = new SeckillOrderSyncEvent(
                    order.getOrderNo(), order.getUserId(), order.getTotalAmount(),
                    order.getPayAmount(), LocalDateTime.now(), tradeNo);
            Message<String> message = BaggageUtils.buildMessage(objectMapper.writeValueAsString(event));
            rocketMQTemplate.syncSend(MqTopic.ORDER_SYNC, message);
        } catch (Exception e) {
            log.error("秒杀订单同步消息发送失败: orderNo={}", order.getOrderNo(), e);
        }
    }
}
