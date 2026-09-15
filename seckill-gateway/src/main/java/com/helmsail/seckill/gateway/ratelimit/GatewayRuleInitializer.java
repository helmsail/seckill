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
 * 规则双轨：Nacos 数据源为动态通道（seckill-gateway-gw-flow.json）——当前未在 Nacos 维护规则数据，
 * 实际生效的是本类装载的兜底规则；将来在 Nacos 配置规则后可动态覆盖，无需发版。
 *
 * 兜底阈值按 3000 QPS 业务目标设定：
 *   - C 端路由 3500 QPS：目标 3000 + 余量，且低于后端 service 集群总容量（3×1200~1500），
 *     超量请求在入口快速拒绝，保护后端不被压垮；
 *   - 运营端路由 500 QPS：人工操作低频，宽松上限。
 *
 * 启动时先装载兜底规则，保证限流任何时刻不失效（fail-closed）：
 *   - Nacos 未配置/不可用：内存保留兜底规则；
 *   - Nacos 配置了规则：数据源拉取后覆盖。
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

    /** 运营管理端兜底 QPS 阈值：人工操作低频，宽松上限（Nacos 配置规则后覆盖） */
    private static final double FALLBACK_QPS_ADMIN = 500;

    /** C 端服务兜底 QPS 阈值：目标 3000 + 余量，且低于后端总容量（Nacos 配置规则后覆盖） */
    private static final double FALLBACK_QPS_SERVICE = 3500;

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
