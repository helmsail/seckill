package com.helmsail.seckill.common.tracing;

import java.util.UUID;

/**
 * 链路追踪 ID 生成器
 *
 * 全链路唯一生成入口：网关为入站请求生成、任务切面为调度执行生成、
 * 秒杀服务为绕过网关的直连请求兜底生成。
 * 格式统一为 32 位无连字符 hex（UUID 去分隔符），保证各入口形态一致、互不冲突。
 */
public final class TraceIdGenerator {

    private TraceIdGenerator() {}

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
