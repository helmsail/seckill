package com.helmsail.seckill.service.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 本地缓存骨架（Caffeine 封装：只做缓存管理，不做加载链组合）
 *
 * - expireAfterWrite（硬过期）→ load → 调用方传入的 loader（全链：快照源 → 回源兜底）
 * - refreshAfterWrite（软过期）→ reload → 调用方传入的 refresher（仅对齐快照源）
 * - 空值保护：loader / refresher 返回 null 一律落内部占位——"缓存空值"把探测拦在本地，
 *   get 出口还原为 null；占位随硬过期自清、被后续刷新覆盖
 *
 * "加载链怎么组、空值形态是什么（null / 空列表）"由调用方的两个函数自行定义。
 */
public class CaffeineCache<V> {

    private static final String NULL_PLACEHOLDER = "##NULL##";

    private final LoadingCache<String, V> cache;

    /**
     * @param writeExpireSeconds 硬过期时间（秒）
     * @param refreshSeconds     软过期时间（秒）
     * @param maximumSize        最大缓存数量
     * @param loader             全链加载：硬过期/首次访问时调用（调用方自组：快照源 → 回源兜底）
     * @param refresher          软过期刷新：仅对齐快照源（返回 null 同落空值保护）
     */
    public CaffeineCache(int writeExpireSeconds,
                         int refreshSeconds,
                         int maximumSize,
                         Function<String, V> loader,
                         Function<String, V> refresher) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(writeExpireSeconds, TimeUnit.SECONDS)
                .refreshAfterWrite(refreshSeconds, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public V load(String key) {
                        return cacheNull(loader.apply(key));
                    }

                    @Override
                    public V reload(String key, V oldValue) {
                        return cacheNull(refresher.apply(key));
                    }
                });
    }

    public V get(String key) {
        V value = cache.get(key);
        if (NULL_PLACEHOLDER.equals(value)) {
            return null;
        }
        return value;
    }

    /**
     * null → 占位（Caffeine 的 load 不允许返回 null；占位让空值本身进入缓存、把探测拦在本地）
     */
    @SuppressWarnings("unchecked")
    private static <V> V cacheNull(V value) {
        return value == null ? (V) NULL_PLACEHOLDER : value;
    }
}
