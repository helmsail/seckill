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
        /** 黑名单有效期（秒）：默认 24 小时，避免误伤用户永久失格（0=永久） */
        private int expireSeconds = 24 * 60 * 60;
        private int windowBeforeSeconds = 300;
        private int windowAfterSeconds = 60;
    }

    @Data
    public static class Result {
        private int expireSeconds = 30;
    }
}
