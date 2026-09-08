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

import java.util.*;

@Slf4j
@Service
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

        refreshActivityInfo(activityNo, activity);
        List<SeckillProductDTO> products = seckillProductService.listByActivityNo(activityNo);
        List<Map<String, Object>> productList = aggregateProductsWithSku(products);
        refreshActivityProductList(activityNo, productList);

        log.info("活动刷新完成: activityNo={}, 商品数={}", activityNo, productList.size());
    }

    private void refreshActivityInfo(String activityNo, ActivityDTO activity) throws Exception {
        String json = objectMapper.writeValueAsString(activity);
        redisService.hSet(SeckillCacheKey.KEY_ACTIVITY_INFO, activityNo, json);
    }

    private List<Map<String, Object>> aggregateProductsWithSku(List<SeckillProductDTO> products) {
        List<Map<String, Object>> productList = new ArrayList<>();
        for (SeckillProductDTO product : products) {
            List<SeckillSkuDTO> skus = seckillSkuService.listBySkProductId(String.valueOf(product.getId()));
            Map<String, Object> map = new HashMap<>();
            map.put("product", product);
            map.put("skus", skus);
            productList.add(map);
        }
        return productList;
    }

    private void refreshActivityProductList(String activityNo, List<Map<String, Object>> productList) throws Exception {
        String json = objectMapper.writeValueAsString(productList);
        redisService.set(String.format(SeckillCacheKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo), json);
    }
}
