package com.helmsail.seckill.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 网关限流兜底规则初始化
 *
 * 背景：Sentinel 内存中的规则只来自数据源（本项目为 Nacos）。
 * Nacos 不可用时规则为空，限流将完全失效（fail-open），这是不能接受的。
 *
 * 因此在启动时先装载一份保守的兜底规则：
 *   - Nacos 正常：数据源拉取到规则后覆盖内存规则，兜底规则仅作启动瞬间的过渡；
 *   - Nacos 异常：内存中保留兜底规则，限流仍然生效（fail-closed）。
 *
 * 兜底阈值刻意放宽（均为 500 QPS），语义是"故障期间不误伤流量"，
 * 正式阈值在 Nacos 中维护（Data ID：seckill-gateway-gw-flow.json）。
 *
 * 两个路由各为一条独立规则，Sentinel 按 resource 分别统计窗口，互不影响；
 * 阈值拆为独立常量，便于按路由差异化设置。
 */
@Slf4j
@Component
public class GatewayRuleInitializer {

    /** 资源名对应 application.yml 中的 routes[].id（resourceMode=0，按路由维度限流） */
    private static final String ROUTE_ADMIN = "seckill-admin";
    private static final String ROUTE_SERVICE = "seckill-service";

    /** 运营管理端兜底 QPS 阈值：Nacos 不可用时的宽松上限，避免故障期间误伤流量 */
    private static final double FALLBACK_QPS_ADMIN = 500;

    /** C 端服务兜底 QPS 阈值：Nacos 不可用时的宽松上限，避免故障期间误伤流量 */
    private static final double FALLBACK_QPS_SERVICE = 500;

    /** QPS 模式固定统计窗口 1 秒 */
    private static final long INTERVAL_SEC = 1;

    /** QPS 限流指标 */
    private static final int GRADE_QPS = 1;

    /** 按路由 ID 维度限流 */
    private static final int MODE_ROUTE_ID = 0;

    @PostConstruct
    public void init() {
        Set<GatewayFlowRule> fallbackRules = Set.of(
                routeRule(ROUTE_ADMIN, FALLBACK_QPS_ADMIN),
                routeRule(ROUTE_SERVICE, FALLBACK_QPS_SERVICE)
        );

        GatewayRuleManager.loadRules(fallbackRules);
        log.info("已装载网关兜底限流规则 {} 条：{}={} QPS，{}={} QPS（Nacos 数据源拉取成功后将覆盖）",
                fallbackRules.size(), ROUTE_ADMIN, FALLBACK_QPS_ADMIN, ROUTE_SERVICE, FALLBACK_QPS_SERVICE);
    }

    /** 构造单条路由的 QPS 限流规则 */
    private static GatewayFlowRule routeRule(String routeId, double qps) {
        return new GatewayFlowRule(routeId)
                .setResourceMode(MODE_ROUTE_ID)
                .setGrade(GRADE_QPS)
                .setCount(qps)
                .setIntervalSec(INTERVAL_SEC);
    }
}
