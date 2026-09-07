package com.helmsail.seckill.job.tracing;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 任务调度链路追踪切面
 *
 * 自动为所有 @XxlJob 方法生成 traceId 并写入 MDC。
 */
@Slf4j
@Aspect
@Component
public class JobTraceAspect {

    private static final String TRACE_ID_KEY = "traceId";

    @Around("@annotation(com.xxl.job.core.handler.annotation.XxlJob)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 生成 traceId
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID_KEY, traceId);

        // 获取任务名称
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        XxlJob xxlJob = signature.getMethod().getAnnotation(XxlJob.class);
        String jobName = xxlJob != null && !xxlJob.value().isEmpty() ? xxlJob.value() : signature.getName();

        log.info("任务启动: jobName={}, traceId={}", jobName, traceId);

        try {
            return joinPoint.proceed();
        } finally {
            log.info("任务结束: jobName={}, traceId={}", jobName, traceId);
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
