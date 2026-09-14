package com.helmsail.seckill.service.activity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.activity.ActivityDTO;
import com.helmsail.seckill.base.activity.ActivityDubboService;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDubboService;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.service.cache.CaffeineCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
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
    private ActivityDubboService activityService;

    @DubboReference
    private SeckillProductSkuDubboService seckillProductSkuService;

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /** 列表缓存条目的键：列表全局唯一，key 仅作条目索引占位 */
    private static final String LIST_CACHE_KEY = "list";

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
            // DubboConsumerExceptionFilter 已还原业务异常；此处再沿 cause 链兜底运行时/代理的再包装
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
        List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        // 与快照同口径：剔除库表带出的库存配额与上下架，DB 运行态值不进缓存
        // （二者由独立 key 承担，查询时统一从 key 拼装）
        for (SeckillProductSkuDTO row : rows) {
            row.setActivityStock(null);
            row.setShelfStatus(null);
        }
        return rows;
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
        return activityListCache.get(LIST_CACHE_KEY);
    }

    public ActivityDTO getActivityByNo(String activityNo) {
        return activityInfoCache.get(activityNo);
    }

    /**
     * 商品列表：快照（静态目录）+ 运行态值拼装，返回查询视图
     *
     * 实时余量 / 上下架状态从库存键、在售键 MGET 读出后填入返回副本
     * （不污染缓存对象），前端无需再逐 SKU 单独拉取。
     */
    public List<ActivityProductVO> getProductListByActivityNo(String activityNo) {
        List<SeckillProductSkuDTO> rows = activityProductCache.get(activityNo);
        if (rows == null) {
            return null;
        }
        List<String> stockKeys = new ArrayList<>(rows.size());
        List<String> shelfKeys = new ArrayList<>(rows.size());
        for (SeckillProductSkuDTO row : rows) {
            stockKeys.add(String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, row.getSkuNo()));
            shelfKeys.add(String.format(SeckillRedisKey.KEY_SKU_SHELF, activityNo, row.getSkuNo()));
        }
        List<String> stocks = stockKeys.isEmpty() ? List.of() : redisService.multiGet(stockKeys);
        List<String> shelves = shelfKeys.isEmpty() ? List.of() : redisService.multiGet(shelfKeys);
        List<ActivityProductVO> list = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ActivityProductVO view = new ActivityProductVO();
            BeanUtils.copyProperties(rows.get(i), view);
            String stock = stocks == null ? null : stocks.get(i);
            String shelf = shelves == null ? null : shelves.get(i);
            view.setRemainingStock(stock == null ? 0 : Integer.parseInt(stock));
            // 上下架以在售键为准（缺失按 0，与购买门禁同口径）
            view.setShelfStatus("1".equals(shelf) ? 1 : 0);
            list.add(view);
        }
        return list;
    }

    public Integer getSkuStock(String activityNo, String skuNo) {
        String stockKey = String.format(SeckillRedisKey.KEY_SKU_STOCK, activityNo, skuNo);
        String stock = redisService.get(stockKey);
        return stock != null ? Integer.parseInt(stock) : 0;
    }

    /**
     * 查 SKU 限购数量（0=不限购；SKU 不在快照中返回 null，由调用方决定放行）
     */
    public Integer getSkuPurchaseLimit(String activityNo, String skuNo) {
        List<SeckillProductSkuDTO> rows = activityProductCache.get(activityNo);
        if (rows == null) {
            return null;
        }
        for (SeckillProductSkuDTO row : rows) {
            if (row.getSkuNo().equals(skuNo)) {
                return row.getPurchaseLimit();
            }
        }
        return null;
    }
}
