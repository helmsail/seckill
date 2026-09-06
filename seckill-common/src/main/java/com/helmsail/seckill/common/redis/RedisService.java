package com.helmsail.seckill.common.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 操作封装
 *
 * 基于 StringRedisTemplate 封装常用操作。
 */
@Component
@ConditionalOnClass(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;
}
