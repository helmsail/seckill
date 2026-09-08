package com.helmsail.seckill.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seckill")
public class SeckillConfig {

    private Check check = new Check();
    private RateLimit rateLimit = new RateLimit();
    private Blacklist blacklist = new Blacklist();
    private Result result = new Result();

    @Data
    public static class Check {
        private boolean rateLimit = true;
        private boolean activityStatus = true;
        private boolean blacklist = true;
        private boolean stock = true;
    }

    @Data
    public static class RateLimit {
        private int windowSeconds = 1;
        private int maxCount = 1;
    }

    @Data
    public static class Blacklist {
        private int expireSeconds = 0;
        private int windowBeforeSeconds = 300;
        private int windowAfterSeconds = 60;
    }

    @Data
    public static class Result {
        private int expireSeconds = 30;
    }
}
