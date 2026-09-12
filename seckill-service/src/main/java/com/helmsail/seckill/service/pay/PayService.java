package com.helmsail.seckill.service.pay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.mq.MqTopic;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderService;
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
import com.helmsail.seckill.support.api.pay.PayNotifyResult;
import com.helmsail.seckill.support.api.pay.PayRequest;
import com.helmsail.seckill.support.api.pay.PayTradeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private static final int QR_CODE_CACHE_TTL = 10 * 60;

    @DubboReference
    private SeckillOrderService seckillOrderService;

    /** 支付渠道 Dubbo 服务（与本地类同名，使用全限定名区分） */
    @DubboReference
    private com.helmsail.seckill.support.api.pay.PayService supportPayService;
    private final RedisService redisService;
    private final RedissonClient redissonClient;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

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
        String qrCode = supportPayService.preCreate(PayChannelType.MOCK, payRequest);

        // 6. 缓存二维码
        redisService.set(cacheKey, qrCode, QR_CODE_CACHE_TTL, TimeUnit.SECONDS);

        return qrCode;
    }

    /**
     * 支付回调（渠道异步通知原始参数）
     *
     * 模拟验签：Mock 渠道恒通过；接入真实渠道时替换 PayChannel.verifyNotify 的真实验签即可。
     * 校验链：验签 → 交易状态 → 订单存在 → 订单待支付 → 金额一致 → 状态流转（base 条件更新）。
     */
    public void payCallback(Map<String, String> params) {
        // 1. 渠道验签 + 解析
        PayNotifyResult notify = supportPayService.verifyNotify(PayChannelType.MOCK, params);
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
                log.warn("回调订单状态非待支付，忽略: orderNo={}, status={}", orderNo, order.getOrderStatus());
                return;
            }
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

            // 3. 状态流转（重复回调/关单竞态由条件更新兜底）
            seckillOrderService.paySuccess(orderNo, notify.getTradeNo());

            // 4. 清除二维码缓存
            redisService.delete(String.format(SeckillRedisKey.KEY_PAY_QRCODE, orderNo));

            // 5. 同步成功订单到主域（发送失败不回滚支付，漏同步由对账补偿）
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
