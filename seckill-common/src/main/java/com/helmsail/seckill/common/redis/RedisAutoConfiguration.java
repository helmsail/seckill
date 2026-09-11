package com.helmsail.seckill.common.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 自动配置
 *
 * 仅当 classpath 存在 Redis 客户端时装配。
 */
@Configuration
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisService redisService(StringRedisTemplate redisTemplate) {
        return new RedisService(redisTemplate);
    }
}
