package com.helmsail.seckill.service.activity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.service.support.CaffeineCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * C 端活动查询服务（纯读）
 *
 * 三层读取：Caffeine（软/硬过期）→ Redis 快照 → Dubbo 回源。
 * 秒杀准入判定已收归 CheckService，本类只负责查询与缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityQueryService {

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
        // 活动状态流转（激活/关闭）依赖 refresh job 同步 Redis 快照，硬过期从 300s 收紧到 60s 缩短 C 端生效延迟窗口
        activityInfoCache = new CaffeineCache<>(60, 30, 1000, this::loadActivityInfo, this::fallbackActivityInfo);
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
        try {
            return activityService.getByActivityNo(key);
        } catch (Exception e) {
            // 活动不存在：返回 null 走空值缓存（穿透保护），准入检查按“不存在”处理。
            // 注意：Caffeine 会将 loader 异常包装为 CompletionException，需沿 cause 链识别 BizException
            BizException bizException = findBizException(e);
            if (bizException != null
                    && SeckillResultEnum.ACTIVITY_NOT_FOUND.getCode().equals(bizException.getCode())) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 沿 cause 链查找 BizException（跨进程/框架包装后类型会丢失）
     */
    private static BizException findBizException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof BizException bizException) {
                return bizException;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
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

    public Integer getSkuStock(String activityNo, String skuNo) {
        String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo);
        String stock = redisService.get(stockKey);
        return stock != null ? Integer.parseInt(stock) : 0;
    }
}
