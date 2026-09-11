package com.helmsail.seckill.service.activity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.activity.ActivityWindows;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.service.cache.CaffeineCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityQueryService {

    private static final String SISMEMBER_LUA = "return redis.call('sismember', KEYS[1], ARGV[1])";

    @DubboReference
    private ActivityService activityService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    private CaffeineCache<List<ActivityDTO>> activityListCache;
    private CaffeineCache<ActivityDTO> activityInfoCache;
    private CaffeineCache<List<SeckillProductSkuDTO>> activityProductCache;

    @PostConstruct
    public void init() {
        activityListCache = new CaffeineCache<>(30, 10, 1000, this::loadActivityList, this::fallbackActivityList);
        activityInfoCache = new CaffeineCache<>(300, 60, 1000, this::loadActivityInfo, this::fallbackActivityInfo);
        activityProductCache = new CaffeineCache<>(300, 60, 1000, this::loadProductList, this::fallbackProductList);
    }

    private List<ActivityDTO> loadActivityList(String key) {
        Map<Object, Object> all = redisService.hGetAll(SeckillRedisKey.KEY_ACTIVITY_INFO);
        List<ActivityDTO> list = new ArrayList<>();
        for (Object value : all.values()) {
            ActivityDTO dto = parse((String) value, ActivityDTO.class);
            // 列表仅展示可购/待开始活动（暂停、关闭不出现）
            if (dto != null && (dto.getActivityStatus() == ActivityStatus.ACTIVE
                    || dto.getActivityStatus() == ActivityStatus.PENDING)) {
                list.add(dto);
            }
        }
        return list;
    }

    private List<ActivityDTO> fallbackActivityList(String key) {
        return activityService.listByStatus(ActivityStatus.ACTIVE);
    }

    private ActivityDTO loadActivityInfo(String key) {
        return parse(redisService.hGet(SeckillRedisKey.KEY_ACTIVITY_INFO, key), ActivityDTO.class);
    }

    private ActivityDTO fallbackActivityInfo(String key) {
        return activityService.getByActivityNo(key);
    }

    private List<SeckillProductSkuDTO> loadProductList(String key) {
        return parse(redisService.get(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, key)),
                new TypeReference<>() {});
    }

    private List<SeckillProductSkuDTO> fallbackProductList(String activityNo) {
        return seckillProductSkuService.listByActivityNo(activityNo);
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

    public List<SeckillProductSkuDTO> getProductListByActivityNo(String activityNo) {
        return activityProductCache.get(activityNo);
    }

    /**
     * 抢购生效判定（缓存数据，仅作前置过滤）
     *
     * 正确性以 processor 的 DB 权威终判为准（ActivityWindows 同一套规则）。
     */
    public boolean isInEffectiveWindow(String activityNo) {
        return ActivityWindows.isInEffectiveWindow(getActivityByNo(activityNo), LocalDateTime.now());
    }

    /**
     * 在售判定（Redis 名单，仅作前置过滤）
     *
     * 正确性以 processor 的 DB 权威状态终判为准；名单滞后最多导致少量请求白跑。
     */
    public boolean isSkuOnShelf(String activityNo, String skuNo) {
        String key = String.format(SeckillRedisKey.KEY_ACTIVITY_SHELF, activityNo);
        Long result = redisService.executeLua(SISMEMBER_LUA, Collections.singletonList(key), skuNo);
        return result != null && result > 0;
    }

    public Integer getSkuStock(String activityNo, String skuNo) {
        String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo);
        String stock = redisService.get(stockKey);
        return stock != null ? Integer.parseInt(stock) : 0;
    }
}
