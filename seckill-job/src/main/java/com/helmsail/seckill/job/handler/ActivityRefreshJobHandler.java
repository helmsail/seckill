package com.helmsail.seckill.job.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.*;
import com.helmsail.seckill.base.product.SeckillProductDTO;
import com.helmsail.seckill.base.product.SeckillProductService;
import com.helmsail.seckill.base.redis.SeckillKey;
import com.helmsail.seckill.base.sku.SeckillSkuDTO;
import com.helmsail.seckill.base.sku.SeckillSkuService;
import com.helmsail.seckill.common.redis.RedisService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 活动刷新任务
 *
 * 定时刷新进行中的活动缓存数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityRefreshJobHandler {

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductService seckillProductService;

    @DubboReference
    private SeckillSkuService seckillSkuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @XxlJob("activityRefreshJob")
    public void execute() {
        log.info("活动刷新任务启动");

        List<ActivityDTO> activities = activityService.listByStatus(ActivityStatus.ACTIVE);
        int refreshed = 0;

        for (ActivityDTO activity : activities) {
            try {
                refreshActivity(activity);
                refreshed++;
            } catch (Exception e) {
                log.error("活动刷新失败: activityNo={}", activity.getActivityNo(), e);
            }
        }

        log.info("活动刷新任务完成，共刷新 {} 个活动", refreshed);
    }

    private void refreshActivity(ActivityDTO activity) throws Exception {
        String activityNo = activity.getActivityNo();
        log.info("开始刷新活动: activityNo={}", activityNo);

        // 1. 刷新活动信息到 Hash
        refreshActivityInfo(activityNo, activity);

        // 2. 查询并刷新活动商品列表
        List<SeckillProductDTO> products = seckillProductService.listByActivityNo(activityNo);
        List<Map<String, Object>> productList = aggregateProductsWithSku(products);
        refreshActivityProductList(activityNo, productList);

        log.info("活动刷新完成: activityNo={}, 商品数={}", activityNo, productList.size());
    }

    private void refreshActivityInfo(String activityNo, ActivityDTO activity) throws Exception {
        String json = objectMapper.writeValueAsString(activity);
        redisService.hSet(SeckillKey.KEY_ACTIVITY_INFO, activityNo, json);
    }

    private List<Map<String, Object>> aggregateProductsWithSku(List<SeckillProductDTO> products) {
        List<Map<String, Object>> productList = new ArrayList<>();

        for (SeckillProductDTO product : products) {
            List<SeckillSkuDTO> skus = seckillSkuService.listBySkProductId(String.valueOf(product.getId()));

            Map<String, Object> productMap = new HashMap<>();
            productMap.put("product", product);
            productMap.put("skus", skus);
            productList.add(productMap);
        }

        return productList;
    }

    private void refreshActivityProductList(String activityNo, List<Map<String, Object>> productList) throws Exception {
        String json = objectMapper.writeValueAsString(productList);
        redisService.set(String.format(SeckillKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo), json);
    }
}
