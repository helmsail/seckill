package com.helmsail.seckill.common.lock;

import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务
 *
 * 基于 Redisson 封装分布式锁操作。
 */
@Slf4j
@Component
@ConditionalOnClass(RedissonClient.class)
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    /**
     * 执行加锁业务逻辑（无返回值）
     */
    public void execute(String key, long waitTime, long leaseTime, TimeUnit unit, Runnable task) throws InterruptedException {
        if (key == null || unit == null || task == null) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        if (waitTime <= 0 || leaseTime <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        RLock lock = redissonClient.getLock(key);
        if (!lock.tryLock(waitTime, leaseTime, unit)) {
            log.warn("获取分布式锁失败: key={}, waitTime={}{}", key, waitTime, unit);
            throw new BizException(ResultEnum.LOCK_ACQUIRE_FAILED);
        }
        try {
            log.debug("获取分布式锁成功: key={}", key);
            task.run();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁: key={}", key);
            }
        }
    }

    /**
     * 执行加锁业务逻辑（有返回值）
     */
    public <T> T execute(String key, long waitTime, long leaseTime, TimeUnit unit, Callable<T> task) throws Exception {
        if (key == null || unit == null || task == null) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        if (waitTime <= 0 || leaseTime <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        RLock lock = redissonClient.getLock(key);
        if (!lock.tryLock(waitTime, leaseTime, unit)) {
            log.warn("获取分布式锁失败: key={}, waitTime={}{}", key, waitTime, unit);
            throw new BizException(ResultEnum.LOCK_ACQUIRE_FAILED);
        }
        try {
            log.debug("获取分布式锁成功: key={}", key);
            return task.call();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁: key={}", key);
            }
        }
    }
}
