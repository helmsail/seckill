package com.helmsail.seckill.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 秒杀准入参数（seckill.*）
 *
 * Nacos 动态配置 + 本地兜底：Nacos 正常时由 seckill-service.yml（group=SECKILL_CONFIG）覆盖，不可用/未配置时回退以下默认值；
 * 支持逐键覆盖（未写入 Nacos 的键保持默认）。运行中修改自动重绑免重启——CheckService 每次请求实时读取本配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "seckill")
public class SeckillConfig {

    private RateLimit rateLimit = new RateLimit();
    private Blacklist blacklist = new Blacklist();

    @Data
    public static class RateLimit {
        private int windowSeconds = 1;
        private int maxCount = 1;
    }

    @Data
    public static class Blacklist {
        /** 黑名单有效期（秒）：默认 24 小时，到期自动解除（取正值，避免误伤用户永久失格） */
        private int expireSeconds = 24 * 60 * 60;
        /** 拉黑窗口起点：开始前多少秒起视为脚本提前抢购（默认 30 分钟，覆盖预热期） */
        private int windowFromSeconds = 30 * 60;
        /** 开放边界：开始前多少秒起放行（默认 3 秒，接住准点请求的时钟抖动与网络延迟） */
        private int windowToSeconds = 3;
    }
}
