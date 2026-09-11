package com.helmsail.seckill.job;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀系统任务调度模块启动类
 *
 * @EnableDubbo 启用 @DubboService/@DubboReference 注解扫描
 * （spring-boot-starter 仅自动绑定 dubbo 配置，不会自动扫描服务注解）
 */
@EnableDubbo
@SpringBootApplication
public class SeckillJobApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillJobApplication.class, args);
    }
}
