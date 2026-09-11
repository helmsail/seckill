package com.helmsail.seckill.common.jwt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 自动配置
 *
 * 仅当配置了 jwt.secret 时装配。
 */
@Configuration
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "jwt", name = "secret")
    @ConditionalOnMissingBean
    public JwtUtils jwtUtils() {
        return new JwtUtils();
    }
}
