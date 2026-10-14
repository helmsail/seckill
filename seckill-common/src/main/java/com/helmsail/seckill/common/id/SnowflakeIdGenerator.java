package com.helmsail.seckill.common.id;

import lombok.extern.slf4j.Slf4j;

/**
 * 雪花算法 ID 生成器（Snowflake，静态单例）
 *
 * ── 64 位结构 ──────────────────────────────────────────────
 *   1  位  符号位（恒 0，保证正数）
 *   41 位  毫秒时间戳（相对 EPOCH，可用约 69 年）
 *   10 位  机器 ID（SNOWFLAKE_MACHINE_ID 手动指定；部署清单分配，缺配即启动失败）
 *   12 位  毫秒内序列（单机每毫秒 4096 个）
 *
 * ── 生成规则 ───────────────────────────────────────────────
 *   同一毫秒：序列 +1，溢出则自旋等到下一毫秒；跨入新毫秒：序列归零；
 *   时钟回拨：≤5ms 等待自愈，>5ms 拒绝生成（宁失败，不产重复 ID）。
 *
 * ── 机器号分配（手动指定）─────────────────────────
 *   由环境变量 SNOWFLAKE_MACHINE_ID 显式指定（0..255），部署时按实例分配并纳入部署清单，
 *   全集群同时刻保证互异；缺失/非法则拒绝启动（fail-fast，不静默兜底）。
 *   扩展说明：接入 K8s 等平台后可平移为 StatefulSet 序号派生或中心化发号，仅需替换本解析实现。
 *
 * 静态单例：全 JVM 唯一实例，类加载时完成机器号解析；
 * 序列推进在实例内互斥（synchronized），保证并发下 ID 不重不漏。
 */
@Slf4j
public final class SnowflakeIdGenerator {

    /** 全局唯一实例：类加载即初始化（机器号派生仅一次） */
    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator();

    // ==================== 位布局 ====================

    /** 起始时间戳（2021-01-01 00:00:00 UTC+8），上线后不可更改 */
    private static final long EPOCH = 1609459200000L;

    /** 机器 ID 位宽（10 位 / 取值 0..255，部署清单手动分配） */
    private static final long MACHINE_ID_BITS = 10L;

    /** 序列位宽（12 位 / 每毫秒 4096 个）与掩码 */
    private static final long SEQUENCE_BITS = 12L;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 时间戳段左移位数：让出机器段与序列段 */
    private static final long TIMESTAMP_SHIFT = MACHINE_ID_BITS + SEQUENCE_BITS;

    /** 时钟回拨容忍上限（毫秒）：以内等待自愈，超出拒绝生成 */
    private static final long MAX_BACKWARD_MS = 5L;

    // ==================== 状态 ====================

    private final long machineId;     // 启动时派生，终身不变
    private long sequence = 0L;       // 当前毫秒内序列
    private long lastTimestamp = -1L; // 上次发号的毫秒时间戳

    private SnowflakeIdGenerator() {
        this.machineId = resolveMachineId();
        log.info("雪花 ID 生成器初始化: machineId={}", machineId);
    }

    /**
     * 生成下一个雪花 ID（全局静态入口）
     */
    public static long nextId() {
        return INSTANCE.generate();
    }

    // ==================== ID 生成 ====================

    /**
     * 生成下一个 ID（实例内同步：序列/时间戳 互斥推进）
     */
    private synchronized long generate() {
        long timestamp = currentTime();

        // 同一毫秒：序列自增，溢出则等到下一毫秒；新毫秒：序列归零
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return assemble(timestamp, sequence);
    }

    /** 拼装 64 位 ID：时间戳段 | 机器段 | 序列段 */
    private long assemble(long timestamp, long sequence) {
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << SEQUENCE_BITS)
                | sequence;
    }

    /** 取当前时间；时钟回拨时小步等待自愈，大步拒绝 */
    private long currentTime() {
        long timestamp = System.currentTimeMillis();
        if (timestamp >= lastTimestamp) {
            return timestamp;
        }

        long backwardMs = lastTimestamp - timestamp;
        if (backwardMs > MAX_BACKWARD_MS) {
            throw new IllegalStateException("时钟回拨过大，拒绝生成 ID: " + backwardMs + "ms");
        }
        log.warn("检测到时钟回拨 {}ms，等待时钟追上", backwardMs);
        return tilNextMillis(lastTimestamp);
    }

    /** 自旋等待到下一毫秒 */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    // ==================== 机器号解析 ====================

    private static final String ENV_MACHINE_ID = "SNOWFLAKE_MACHINE_ID";

    /**
     * 机器号解析（0..255）：仅接受 SNOWFLAKE_MACHINE_ID 手动指定；
     * 缺失或非法即抛异常（fail-fast，不静默兜底）。
     */
    private static long resolveMachineId() {
        String value = System.getenv(ENV_MACHINE_ID);
        Long machineId = (value == null || value.isBlank()) ? null : parseMachineId(value);
        if (machineId == null) {
            throw new IllegalStateException("机器号缺失或非法：" + ENV_MACHINE_ID + " -> " + value
                    + "（部署清单手动分配，0..255，全集群同时刻互异）");
        }
        log.info("机器号来源: {}（{}）", ENV_MACHINE_ID, machineId);
        return machineId;
    }

    /** 机器号 = 0..255 十进制字符串 */
    private static Long parseMachineId(String value) {
        try {
            long id = Long.parseLong(value.trim());
            return (id >= 0 && id <= 255) ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
