package com.helmsail.seckill.common.tracing;

import com.helmsail.seckill.common.tracing.http.HttpBaggageFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 链路追踪自动配置
 *
 * Servlet 环境下自动注册 HTTP 入口过滤器；
 * Dubbo 侧通过 SPI 过滤器自动生效（见 META-INF/dubbo）。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public HttpBaggageFilter httpBaggageFilter() {
        return new HttpBaggageFilter();
    }
}
