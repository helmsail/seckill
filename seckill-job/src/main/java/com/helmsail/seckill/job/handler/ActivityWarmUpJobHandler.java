package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.*;
import com.helmsail.seckill.base.product.SeckillProductDTO;
import com.helmsail.seckill.base.product.SeckillProductService;
import com.helmsail.seckill.base.redis.SeckillCacheKey;
import com.helmsail.seckill.base.sku.SeckillSkuDTO;
import com.helmsail.seckill.base.sku.SeckillSkuService;
import com.helmsail.seckill.common.redis.RedisService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityWarmUpJobHandler {

    private static final int WARM_UP_WINDOW_SECONDS = 30 * 60;

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductService seckillProductService;

    @DubboReference
    private SeckillSkuService seckillSkuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @XxlJob("activityWarmUpJob")
    public void execute() {
        log.info("活动预热任务启动");

        List<ActivityDTO> activities = activityService.listByStatus(ActivityStatus.PENDING);
        LocalDateTime now = LocalDateTime.now();
        int warmedUp = 0;

        for (ActivityDTO activity : activities) {
            if (!isInWarmUpWindow(activity, now)) {
                continue;
            }
            try {
                warmUpActivity(activity);
                warmedUp++;
            } catch (Exception e) {
                log.error("活动预热失败: activityNo={}", activity.getActivityNo(), e);
            }
        }

        log.info("活动预热任务完成，共预热 {} 个活动", warmedUp);
    }

    private boolean isInWarmUpWindow(ActivityDTO activity, LocalDateTime now) {
        long secondsUntilStart = ChronoUnit.SECONDS.between(now, activity.getStartTime());
        return secondsUntilStart >= 0 && secondsUntilStart <= WARM_UP_WINDOW_SECONDS;
    }

    private void warmUpActivity(ActivityDTO activity) throws Exception {
        String activityNo = activity.getActivityNo();
        log.info("开始预热活动: activityNo={}", activityNo);

        cacheActivityInfo(activityNo, activity);
        List<SeckillProductDTO> products = seckillProductService.listByActivityNo(activityNo);
        List<Map<String, Object>> productList = aggregateProductsWithSku(activityNo, products);
        cacheActivityProductList(activityNo, productList);

        log.info("活动预热完成: activityNo={}, 商品数={}", activityNo, productList.size());
    }

    private void cacheActivityInfo(String activityNo, ActivityDTO activity) throws Exception {
        String json = objectMapper.writeValueAsString(activity);
        redisService.hSet(SeckillCacheKey.KEY_ACTIVITY_INFO, activityNo, json);
    }

    private List<Map<String, Object>> aggregateProductsWithSku(String activityNo, List<SeckillProductDTO> products) {
        List<Map<String, Object>> productList = new ArrayList<>();
        for (SeckillProductDTO product : products) {
            List<SeckillSkuDTO> skus = seckillSkuService.listBySkProductId(String.valueOf(product.getId()));

            Map<String, Object> productMap = new HashMap<>();
            productMap.put("product", product);
            productMap.put("skus", skus);
            productList.add(productMap);

            cacheSkuStock(skus);
        }
        return productList;
    }

    private void cacheSkuStock(List<SeckillSkuDTO> skus) {
        for (SeckillSkuDTO sku : skus) {
            String stockKey = String.format(SeckillCacheKey.KEY_SKU_STOCK, sku.getSkuNo());
            redisService.setIfAbsent(stockKey, String.valueOf(sku.getActivityStock()));
        }
    }

    private void cacheActivityProductList(String activityNo, List<Map<String, Object>> productList) throws Exception {
        String json = objectMapper.writeValueAsString(productList);
        redisService.set(String.format(SeckillCacheKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo), json);
    }
}
