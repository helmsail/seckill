package com.helmsail.seckill.processor.seckill;

import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityDubboService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDubboService;
import com.helmsail.seckill.base.seckill.SeckillRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

/**
 * 秒杀业务二次校验（DB 权威；门禁 Redis 预过滤的纠偏点）
 *
 * 消息在 MQ 排队期间，运营操作（关闭/暂停活动、下架商品）可能已改变状态，故以 DB 为准复核：
 * 活动进行中 + SKU 存在且在售；失败仅记日志（原因供人工排查），调用方按业务失败统一收敛。
 */
@Slf4j
@Service
public class SeckillCheckService {

    @DubboReference
    private ActivityDubboService activityService;

    @DubboReference
    private SeckillProductSkuDubboService seckillProductSkuService;

    /**
     * 二次校验：活动进行中 + SKU 存在且在售
     *
     * 活动级 DB 状态终判：消息排队期间活动可能恰好关闭；无需判时间——消息只会产生于开始之后。
     *
     * @return true 通过；false 拒绝（具体原因见日志）
     */
    public boolean check(SeckillRequest request) {
        ActivityDTO activity = activityService.getByActivityNo(request.getActivityNo());
        if (activity == null || activity.getActivityStatus() != ActivityStatus.ACTIVE) {
            log.warn("二次校验未通过：活动不在进行中: activityNo={}", request.getActivityNo());
            return false;
        }
        SeckillProductSkuDTO sku = seckillProductSkuService.getByActivityNoAndSkuNo(
                request.getActivityNo(), request.getSkuNo());
        if (sku == null) {
            log.warn("二次校验未通过：SKU不存在: activityNo={}, skuNo={}",
                    request.getActivityNo(), request.getSkuNo());
            return false;
        }
        // DB 权威状态终判（Redis 名单滞后时的兜底裁定点）
        if (sku.getShelfStatus() == null || sku.getShelfStatus() != 1) {
            log.warn("二次校验未通过：商品已下架: activityNo={}, skuNo={}",
                    request.getActivityNo(), request.getSkuNo());
            return false;
        }
        return true;
    }
}
