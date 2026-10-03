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
import java.util.concurrent.TimeUnit;

/**
 * C 端活动查询服务
 *
 * 三层读取：Caffeine（软/硬过期）→ Redis 快照 → Dubbo 回源；
 * 回源结果同口径回填 Redis（无 TTL）——读路径为兜底写入者，预热任务仍是常态刷新者；
 * 回源确认不存在/为空时写短 TTL 负标记（跨实例短路回源，TTL 自清）。
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

    /** 负标记 TTL（秒）：跨实例短路回源的共享窗口，TTL 自清；不宜过长，保证"新建实体"尽快可见 */
    private static final long NULL_MARKER_TTL_SECONDS = 60L;

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
        // 负标记（跨实例共享、短 TTL）：命中即短路，不再探测 base
        if (redisService.hasKey(String.format(SeckillRedisKey.KEY_ACTIVITY_INFO_NULL, key))) {
            return null;
        }
        try {
            ActivityDTO activity = activityService.getByActivityNo(key);
            backfillActivityInfo(key, activity);
            return activity;
        } catch (Exception e) {
            // 活动不存在：写短 TTL 负标记（跨实例短路回源、自清），返回 null 走空值缓存（穿透保护）
            // DubboConsumerExceptionFilter 已还原业务异常；此处再沿 cause 链兜底运行时/代理的再包装
            BizException bizException = findBizException(e);
            if (bizException != null
                    && SeckillResultEnum.ACTIVITY_NOT_FOUND.getCode().equals(bizException.getCode())) {
                writeNullMarker(SeckillRedisKey.KEY_ACTIVITY_INFO_NULL, key);
                return null;
            }
            throw e;
        }
    }

    /**
     * 回源回填：把兜底取到的活动信息同口径写回 Redis（无 TTL，与预热任务写入口径一致）
     *
     * 读路径由此成为"兜底写入者"：仅 Redis 未命中时写入，job 的分钟级覆盖仍是常态刷新；
     * 回填失败仅记日志，不影响本次读取。
     */
    private void backfillActivityInfo(String activityNo, ActivityDTO activity) {
        if (activity == null) {
            return;
        }
        try {
            redisService.hSet(SeckillRedisKey.KEY_ACTIVITY_INFO, activityNo,
                    objectMapper.writeValueAsString(activity));
            log.info("活动信息回源回填 Redis: activityNo={}", activityNo);
        } catch (Exception e) {
            log.warn("活动信息回填 Redis 失败（忽略，不影响本次读取）: activityNo={}", activityNo, e);
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
        // 负标记（跨实例共享、短 TTL）：命中即短路，不再探测 base（空结果按空列表处理）
        if (redisService.hasKey(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_NULL, activityNo))) {
            return List.of();
        }
        List<SeckillProductSkuDTO> rows = seckillProductSkuService.listByActivityNo(activityNo);
        if (rows == null || rows.isEmpty()) {
            writeNullMarker(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_NULL, activityNo);
            return rows;
        }
        // 与快照同口径：剔除库表带出的库存配额与上下架，DB 运行态值不进缓存
        // （二者由独立 key 承担，查询时统一从 key 拼装）
        for (SeckillProductSkuDTO row : rows) {
            row.setActivityStock(null);
            row.setShelfStatus(null);
        }
        backfillProductSnapshot(activityNo, rows);
        return rows;
    }

    /**
     * 回源回填：把兜底取到的商品快照同口径写回 Redis（无 TTL）
     *
     * 空列表不回填——保留预热任务"空活动不写键"的约定，避免为可删除的空活动留残留键。
     */
    private void backfillProductSnapshot(String activityNo, List<SeckillProductSkuDTO> rows) {
        try {
            redisService.set(String.format(SeckillRedisKey.KEY_ACTIVITY_PRODUCT_LIST, activityNo),
                    objectMapper.writeValueAsString(rows));
            log.info("商品快照回源回填 Redis: activityNo={}, size={}", activityNo, rows.size());
        } catch (Exception e) {
            log.warn("商品快照回填 Redis 失败（忽略，不影响本次读取）: activityNo={}", activityNo, e);
        }
    }

    /**
     * 写"不存在/为空"负标记：短 TTL、TTL 自清、跨实例共享，用于短路回源（不再探测 base）
     */
    private void writeNullMarker(String keyPattern, String key) {
        try {
            redisService.set(String.format(keyPattern, key), "1", NULL_MARKER_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("负标记写入失败（忽略，不影响本次读取）: key={}", key, e);
        }
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
