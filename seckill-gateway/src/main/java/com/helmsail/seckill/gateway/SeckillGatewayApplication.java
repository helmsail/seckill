package com.helmsail.seckill.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication
public class SeckillGatewayApplication {

    public static void main(String[] args) {
        // WebFlux 异步链路下，让 Reactor Context 中的 traceId/userId 自动同步到 MDC（含跨线程/异步日志）
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(SeckillGatewayApplication.class, args);
    }
}
