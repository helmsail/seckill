package com.helmsail.seckill.common.exception;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 异常处理器自动配置
 *
 * 仅 Servlet 环境注册 WebMvc 全局异常处理器；
 * Reactive 环境（网关）的异常由网关自己的 ErrorWebExceptionHandler 实现。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ExceptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public WebMvcExceptionHandler webMvcExceptionHandler() {
        return new WebMvcExceptionHandler();
    }
}
