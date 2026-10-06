package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.base.order.CreateSeckillOrderRequest;
import com.helmsail.seckill.base.order.SeckillOrderDTO;
import com.helmsail.seckill.base.order.SeckillOrderDubboService;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDubboService;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 秒杀订单生成（建单 + 回执证伪）
 *
 * 单价取 DB 权威数据（SKU 缺失按未建单处理，回补已扣资源）；建单回执丢失时查证订单表裁定归属；
 * 延迟关单消息由 consumer 编排发送（发送失败由 closeOrderResendJob 扫描兜底）。
 */
@Slf4j
@Service
public class SeckillOrderCreateService {

    @DubboReference
    private SeckillOrderDubboService seckillOrderService;

    @DubboReference
    private SeckillProductSkuDubboService seckillProductSkuService;

    /**
     * 建单（SKU 自取；含回执证伪）
     *
     * @return 订单号；null 表示确定未建单（调用方回补已扣资源并按失败收敛）
     */
    public String createOrder(SeckillRequest request) {
        SeckillProductSkuDTO sku = seckillProductSkuService.getByActivityNoAndSkuNo(
                request.getActivityNo(), request.getSkuNo());
        if (sku == null) {
            log.error("建单失败：SKU不存在: activityNo={}, skuNo={}",
                    request.getActivityNo(), request.getSkuNo());
            return null;
        }
        String orderNo;
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
            orderNo = seckillOrderService.createOrder(orderRequest);
        } catch (Exception e) {
            log.error("创建订单失败: {}", e.getMessage());
            // createOrder 异常/超时可能“实际已建单但回执丢失”：查证，已建则按成功处理（避免双重回补造成账目不一致）
            SeckillOrderDTO existing = seckillOrderService.getByTraceId(
                    Long.parseLong(request.getUserId()), request.getTraceId());
            if (existing == null) {
                return null;
            }
            log.warn("createOrder 响应异常但订单已建，按成功处理: traceId={}, orderNo={}",
                    request.getTraceId(), existing.getOrderNo());
            orderNo = existing.getOrderNo();
        }

        return orderNo;
    }
}
