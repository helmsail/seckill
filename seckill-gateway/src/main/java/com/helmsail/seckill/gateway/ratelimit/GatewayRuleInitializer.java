package com.helmsail.seckill.gateway.ratelimit;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 网关限流兜底规则初始化
 *
 * 规则双轨：Nacos 数据源为动态通道（seckill-gateway-gw-flow.json）——当前未在 Nacos 维护规则数据，
 * 实际生效的是本类装载的兜底规则；将来在 Nacos 配置规则后可动态覆盖，无需发版。
 *
 * 兜底阈值按 5000 QPS 业务目标设定（含前端轮询放大；阈值 = min(业务目标×1.2, 后端容量×0.9)，
 * 后端容量变化后需同步校准）：
 *   - C 端交易路由（秒杀提交/支付）6000 QPS：目标 5000 + 20% 余量，超量在入口快速拒绝；
 *   - C 端读路由（结果键轮询/订单状态轮询/活动浏览）12000 QPS：轮询洪峰口径——
 *     提交 5000/s 时在途等待者约 5k~10k 个、每人约 1 QPS，独立统计不挤占交易；
 *   - 运营端路由 500 QPS：人工操作低频，宽松上限。
 *
 * 网关多实例部署（N 拓扑 3 实例）时，Sentinel 按实例独立计数，
 * 阈值口径 = 「每实例 = 总目标 ÷ 实例数」：本地单实例 6000/12000/500；
 * 云端 G1~G3 三实例由 .env 注入 GATEWAY_QPS_SERVICE=2000 / GATEWAY_QPS_READ=4000 / GATEWAY_QPS_ADMIN=500。
 *
 * 阈值统一由配置提供（单一来源）：application.yml 的 gateway.ratelimit.qps.*，
 * 其中默认值与环境变量表达式（GATEWAY_QPS_*）已在 yml 声明，代码不再硬编码阈值。
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
    private static final String ROUTE_SERVICE_READ = "seckill-service-read";

    /** 运营管理端兜底 QPS 阈值：人工操作低频，宽松上限（配置 gateway.ratelimit.qps.admin；Nacos 配置规则后覆盖） */
    @Value("${gateway.ratelimit.qps.admin:500}")
    private double qpsAdmin;

    /** C 端交易兜底 QPS 阈值：秒杀提交目标 5000 + 20% 余量（配置 gateway.ratelimit.qps.service；Nacos 配置规则后覆盖） */
    @Value("${gateway.ratelimit.qps.service:6000}")
    private double qpsService;

    /** C 端读兜底 QPS 阈值：轮询洪峰口径约 5k~10k（配置 gateway.ratelimit.qps.service-read；Nacos 配置规则后覆盖） */
    @Value("${gateway.ratelimit.qps.service-read:12000}")
    private double qpsServiceRead;

    /** QPS 模式固定统计窗口 1 秒 */
    private static final long INTERVAL_SEC = 1;

    /** QPS 限流指标 */
    private static final int GRADE_QPS = 1;

    /** 按路由 ID 维度限流 */
    private static final int MODE_ROUTE_ID = 0;

    @PostConstruct
    public void init() {
        Set<GatewayFlowRule> fallbackRules = Set.of(
                routeRule(ROUTE_ADMIN, qpsAdmin),
                routeRule(ROUTE_SERVICE, qpsService),
                routeRule(ROUTE_SERVICE_READ, qpsServiceRead)
        );

        GatewayRuleManager.loadRules(fallbackRules);
        log.info("已装载网关兜底限流规则 {} 条：{}={} QPS，{}={} QPS，{}={} QPS（Nacos 数据源拉取成功后将覆盖）",
                fallbackRules.size(), ROUTE_ADMIN, qpsAdmin,
                ROUTE_SERVICE, qpsService, ROUTE_SERVICE_READ, qpsServiceRead);
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
