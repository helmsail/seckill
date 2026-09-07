package com.helmsail.seckill.common.exception;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 异常处理器自动配置
 *
 * 通过 @Configuration + @Bean 注册异常处理器，支持 AutoConfiguration.imports 自动装配。
 */
@Configuration
public class ExceptionAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public WebMvcExceptionHandler webMvcExceptionHandler() {
        return new WebMvcExceptionHandler();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    public WebFluxExceptionHandler webFluxExceptionHandler() {
        return new WebFluxExceptionHandler();
    }
}
