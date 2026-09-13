package com.helmsail.seckill.job.tracing;

import com.helmsail.seckill.common.tracing.BaggageKeys;
import com.helmsail.seckill.common.tracing.TraceIdGenerator;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 任务调度链路追踪切面
 *
 * 自动为所有 @XxlJob 方法生成 traceId（与网关同一生成器）并写入 MDC；
 * 同时经 XxlJobHelper 写入执行日志，使调度中心「执行日志」中可见 traceId。
 */
@Slf4j
@Aspect
@Component
public class JobTraceAspect {

    @Around("@annotation(com.xxl.job.core.handler.annotation.XxlJob)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 生成 traceId
        String traceId = TraceIdGenerator.generate();
        MDC.put(BaggageKeys.TRACE_ID, traceId);

        // 获取任务名称
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        XxlJob xxlJob = signature.getMethod().getAnnotation(XxlJob.class);
        String jobName = xxlJob != null && !xxlJob.value().isEmpty() ? xxlJob.value() : signature.getName();

        log.info("任务启动: jobName={}, traceId={}", jobName, traceId);
        // 双通道输出：XxlJobHelper 写执行日志，调度中心可直接看到 traceId 关联全链路
        XxlJobHelper.log("任务启动: jobName={}, traceId={}", jobName, traceId);

        try {
            return joinPoint.proceed();
        } finally {
            log.info("任务结束: jobName={}, traceId={}", jobName, traceId);
            XxlJobHelper.log("任务结束: jobName={}, traceId={}", jobName, traceId);
            MDC.remove(BaggageKeys.TRACE_ID);
        }
    }
}
