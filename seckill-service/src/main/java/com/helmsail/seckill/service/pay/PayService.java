package com.helmsail.seckill.service.pay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderService;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.support.api.pay.PayRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private static final String QR_CODE_CACHE_KEY = "seckill:pay:qrcode:%s";
    private static final int QR_CODE_CACHE_TTL = 10 * 60;

    @DubboReference
    private SeckillOrderService seckillOrderService;

    private final com.helmsail.seckill.support.api.pay.PayService supportPayService;
    private final RedisService redisService;
    private final RedissonClient redissonClient;

    public String prePay(String orderNo) {
        // 1. 检查缓存
        String cacheKey = String.format(QR_CODE_CACHE_KEY, orderNo);
        String cachedQrCode = redisService.get(cacheKey);
        if (cachedQrCode != null) {
            return cachedQrCode;
        }

        // 2. 查询订单
        SeckillOrderDTO order = seckillOrderService.getByOrderNo(orderNo);

        // 3. 调用支付宝预支付
        PayRequest payRequest = new PayRequest();
        payRequest.setSubject(order.getRemark());
        payRequest.setOutTradeNo(order.getOrderNo());
        payRequest.setTotalAmount(String.valueOf(order.getPayAmount()));
        String qrCode = supportPayService.preCreate(payRequest);

        // 4. 缓存二维码
        redisService.set(cacheKey, qrCode, QR_CODE_CACHE_TTL, TimeUnit.SECONDS);

        return qrCode;
    }

    public void payCallback(String orderNo) {
        String lockKey = "seckill:pay:lock:" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            lock.lock(10, TimeUnit.SECONDS);
            seckillOrderService.paySuccess(orderNo);
            // 清除二维码缓存
            redisService.delete(String.format(QR_CODE_CACHE_KEY, orderNo));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
