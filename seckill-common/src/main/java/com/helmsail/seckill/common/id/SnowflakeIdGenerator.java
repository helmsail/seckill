package com.helmsail.seckill.common.id;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

/**
 * 雪花算法 ID 生成器
 *
 * 由 IdAutoConfiguration 自动注册。
 * 机器号（datacenterId + workerId，共 10 位）纯自动派生：
 * 优先取本机非回环 IPv4 的后两段，取不到时回退 hostname 哈希。
 */
@Slf4j
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1609459200000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long MACHINE_ID_MASK = 0x3FFL; // 10 位

    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        long machineId = resolveMachineId();
        this.datacenterId = (machineId >> 5) & 0x1F;
        this.workerId = machineId & 0x1F;
        log.info("雪花机器号自动派生: datacenterId={}, workerId={}", datacenterId, workerId);
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << (WORKER_ID_BITS + DATACENTER_ID_BITS + SEQUENCE_BITS))
                | (datacenterId << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;
    }

    /**
     * 自动派生机器号（10 位）：
     * 1. 本机非回环 IPv4 的后两段：(第三段 << 8 | 第四段) & 0x3FF
     * 2. 取不到时回退 hostname 哈希
     */
    private static long resolveMachineId() {
        Long fromIp = machineIdFromIp();
        if (fromIp != null) {
            return fromIp;
        }
        return Math.floorMod(localHostName().hashCode(), 1024);
    }

    private static Long machineIdFromIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        byte[] ip = addr.getAddress();
                        return ((long) (ip[2] & 0xFF) << 8 | (ip[3] & 0xFF)) & MACHINE_ID_MASK;
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return null;
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
