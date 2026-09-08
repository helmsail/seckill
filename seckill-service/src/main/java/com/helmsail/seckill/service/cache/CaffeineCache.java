package com.helmsail.seckill.service.cache;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Caffeine 缓存实例
 *
 * 借用 Caffeine 原生过期机制：
 * - expireAfterWrite（硬过期）→ load() → Redis + 回源
 * - refreshAfterWrite（软过期）→ reload() → 仅 Redis
 *
 * 内置缓存穿透保护：空值缓存短时间，防止重复查询不存在的数据。
 */
public class CaffeineCache<V> {

    private static final String NULL_PLACEHOLDER = "##NULL##";

    private final LoadingCache<String, V> cache;
    private final Function<String, V> redisLoader;
    private final Function<String, V> fallbackLoader;

    /**
     * @param writeExpireSeconds 硬过期时间（秒）
     * @param refreshSeconds     软过期时间（秒）
     * @param maximumSize        最大缓存数量
     * @param redisLoader        从 Redis 加载
     * @param fallbackLoader     回源加载（硬过期且 Redis 没有时调用）
     */
    public CaffeineCache(int writeExpireSeconds,
                         int refreshSeconds,
                         int maximumSize,
                         Function<String, V> redisLoader,
                         Function<String, V> fallbackLoader) {
        this.redisLoader = redisLoader;
        this.fallbackLoader = fallbackLoader;

        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .recordStats()
                .expireAfterWrite(writeExpireSeconds, TimeUnit.SECONDS)
                .refreshAfterWrite(refreshSeconds, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public @NonNull V load(@NonNull String key) {
                        return loadWithFallback(key);
                    }

                    @Override
                    public @Nullable V reload(@NonNull String key, @NonNull V oldValue) {
                        return loadFromRedis(key);
                    }
                });
    }

    private V loadWithFallback(String key) {
        V value = redisLoader.apply(key);
        if (value != null) {
            return value;
        }
        V fallbackValue = fallbackLoader.apply(key);
        if (fallbackValue == null) {
            return (V) NULL_PLACEHOLDER;
        }
        return fallbackValue;
    }

    private V loadFromRedis(String key) {
        return redisLoader.apply(key);
    }

    @SuppressWarnings("unchecked")
    public V get(String key) {
        V value = cache.get(key);
        if (NULL_PLACEHOLDER.equals(value)) {
            return null;
        }
        return value;
    }

    public void put(String key, V value) {
        if (value != null) {
            cache.put(key, value);
        }
    }

    public void evict(String key) {
        cache.invalidate(key);
    }

    /**
     * 获取缓存统计信息
     */
    public String stats() {
        return cache.stats().toString();
    }

    /**
     * 当前缓存大小
     */
    public long size() {
        return cache.estimatedSize();
    }
}
