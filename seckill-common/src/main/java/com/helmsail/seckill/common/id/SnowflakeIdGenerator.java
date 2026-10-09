package com.helmsail.seckill.common.id;

import lombok.extern.slf4j.Slf4j;

/**
 * 雪花算法 ID 生成器（Snowflake，静态单例）
 *
 * ── 64 位结构 ──────────────────────────────────────────────
 *   1  位  符号位（恒 0，保证正数）
 *   41 位  毫秒时间戳（相对 EPOCH，可用约 69 年）
 *   10 位  机器 ID（SNOWFLAKE_MACHINE_ID 显式覆盖；缺省取宿主 IP 第四段）
 *   12 位  毫秒内序列（单机每毫秒 4096 个）
 *
 * ── 生成规则 ───────────────────────────────────────────────
 *   同一毫秒：序列 +1，溢出则自旋等到下一毫秒；跨入新毫秒：序列归零；
 *   时钟回拨：≤5ms 等待自愈，>5ms 拒绝生成（宁失败，不产重复 ID）。
 *
 * ── 机器号派生（两级）──────────────────────────────────
 *   1) 显式覆盖：环境变量 SNOWFLAKE_MACHINE_ID（0..255；同机多实例部署时必须显式区分，
 *      否则同机双实例 HOST_IP 相同，同一毫秒各自序列会发出重复 ID）；显式设置但非法 → fail-fast；
 *   2) 默认派生：读取环境变量 HOST_IP（部署时按台注入的宿主内网 IP），取 IPv4 第四段；
 *      缺失/非法则拒绝生成（fail-fast，不静默兜底）。
 *
 * 静态单例：全 JVM 唯一实例，类加载时完成机器号派生；
 * 序列推进在实例内互斥（synchronized），保证并发下 ID 不重不漏。
 */
@Slf4j
public final class SnowflakeIdGenerator {

    /** 全局唯一实例：类加载即初始化（机器号派生仅一次） */
    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator();

    // ==================== 位布局 ====================

    /** 起始时间戳（2021-01-01 00:00:00 UTC+8），上线后不可更改 */
    private static final long EPOCH = 1609459200000L;

    /** 机器 ID 位宽（10 位 / 取值 0..255，取宿主 IP 第四段） */
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

    private static final String ENV_HOST_IP = "HOST_IP";

    private static final String ENV_MACHINE_ID = "SNOWFLAKE_MACHINE_ID";

    /**
     * 机器号解析（0..255）：
     * 1) SNOWFLAKE_MACHINE_ID 显式覆盖优先（同机多实例部署场景；显式非法即 fail-fast）；
     * 2) 否则取 HOST_IP 的 IPv4 第四段；缺失或非法即抛异常（fail-fast，不静默兜底）。
     */
    private static long resolveMachineId() {
        String override = System.getenv(ENV_MACHINE_ID);
        if (override != null && !override.isBlank()) {
            Long machineId = parseMachineId(override);
            if (machineId == null) {
                throw new IllegalStateException("机器号覆盖非法： " + ENV_MACHINE_ID + " -> " + override);
            }
            log.info("机器号来源: {}（{}）", ENV_MACHINE_ID, machineId);
            return machineId;
        }
        String ip = System.getenv(ENV_HOST_IP);
        Long machineId = ipSuffix(ip);
        if (machineId == null) {
            throw new IllegalStateException("机器号派生失败：HOST_IP 缺失或非法 -> " + ip);
        }
        log.info("机器号来源: {}（{}） -> {}", ENV_HOST_IP, ip, machineId);
        return machineId;
    }

    /** 机器号 = 0..255 十进制字符串（显式覆盖用） */
    private static Long parseMachineId(String value) {
        try {
            long id = Long.parseLong(value.trim());
            return (id >= 0 && id <= 255) ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 机器号 = IPv4 第四段（"…216.102" → 102）：单网段部署下第四段互异即唯一 */
    private static Long ipSuffix(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            return null;
        }
        try {
            int fourth = Integer.parseInt(parts[3]);
            if (fourth < 0 || fourth > 255) {
                return null;
            }
            return (long) fourth;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
