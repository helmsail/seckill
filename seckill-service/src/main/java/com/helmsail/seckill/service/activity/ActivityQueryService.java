package com.helmsail.seckill.service.activity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.product.SeckillProductDTO;
import com.helmsail.seckill.base.product.SeckillProductService;
import com.helmsail.seckill.base.redis.SeckillKey;
import com.helmsail.seckill.base.sku.SeckillSkuDTO;
import com.helmsail.seckill.base.sku.SeckillSkuService;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.service.cache.CaffeineCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * C 端活动查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityQueryService {

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductService seckillProductService;

    @DubboReference
    private SeckillSkuService seckillSkuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    private CaffeineCache<List<ActivityDTO>> activityListCache;
    private CaffeineCache<ActivityDTO> activityInfoCache;
    private CaffeineCache<List<Map<String, Object>>> activityProductCache;

    @PostConstruct
    public void init() {
        activityListCache = new CaffeineCache<>(30, 10, 1000, this::loadActivityList, this::fallbackActivityList);
        activityInfoCache = new CaffeineCache<>(300, 60, 1000, this::loadActivityInfo, this::fallbackActivityInfo);
        activityProductCache = new CaffeineCache<>(300, 60, 1000, this::loadProductList, this::fallbackProductList);
    }

    private List<ActivityDTO> loadActivityList(String key) {
        Map<Object, Object> all = redisService.hGetAll(SeckillKey.KEY_ACTIVITY_INFO);
        List<ActivityDTO> list = new ArrayList<>();
        for (Object value : all.values()) {
            ActivityDTO dto = parse((String) value, ActivityDTO.class);
            if (dto != null) list.add(dto);
        }
        return list;
    }

    private List<ActivityDTO> fallbackActivityList(String key) {
        return activityService.listByStatus(ActivityStatus.ACTIVE);
    }

    private ActivityDTO loadActivityInfo(String key) {
        return parse(redisService.hGet(SeckillKey.KEY_ACTIVITY_INFO, key), ActivityDTO.class);
    }

    private ActivityDTO fallbackActivityInfo(String key) {
        return activityService.getByActivityNo(key);
    }

    private List<Map<String, Object>> loadProductList(String key) {
        return parse(redisService.get(String.format(SeckillKey.KEY_ACTIVITY_PRODUCT_LIST, key)), new TypeReference<>() {});
    }

    private List<Map<String, Object>> fallbackProductList(String activityNo) {
        List<SeckillProductDTO> products = seckillProductService.listByActivityNo(activityNo);
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

    private <T> T parse(String json, Class<T> clazz) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("解析失败: {}", e.getMessage());
            return null;
        }
    }

    private <T> T parse(String json, TypeReference<T> typeRef) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.error("解析失败: {}", e.getMessage());
            return null;
        }
    }

    public List<ActivityDTO> listActivities() {
        return activityListCache.get("list");
    }

    public ActivityDTO getActivityByNo(String activityNo) {
        return activityInfoCache.get(activityNo);
    }

    public List<Map<String, Object>> getProductListByActivityNo(String activityNo) {
        return activityProductCache.get(activityNo);
    }

    public Integer getSkuStock(String skuNo) {
        String stockKey = String.format(SeckillKey.KEY_SKU_STOCK, skuNo);
        String stock = redisService.get(stockKey);
        return stock != null ? Integer.parseInt(stock) : 0;
    }
}
