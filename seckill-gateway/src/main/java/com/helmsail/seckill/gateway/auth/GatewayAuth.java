package com.helmsail.seckill.gateway.auth;

import com.helmsail.seckill.common.user.Role;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 网关认证共享常量与白名单
 *
 * 供 AuthenticationGlobalFilter / AuthorizationGlobalFilter 共用。
 */
public final class GatewayAuth {

    private GatewayAuth() {}

    /** exchange 属性键：认证过滤器写入的角色，供授权过滤器读取 */
    public static final String ATTR_ROLE = "gateway.auth.role";

    /** 管理端所需角色（与 t_user.role 对齐，见 common 的 Role 枚举） */
    public static final Role ADMIN_ROLE = Role.OPERATOR;

    /** 管理端路径前缀 */
    public static final String ADMIN_PATH = "/api/admin/**";

    /**
     * 免认证路径（Ant 模式）
     *
     * 新增免认证接口仅需在此处添加，认证/授权过滤器自动生效。
     */
    private static final List<String> WHITELIST = List.of(
            "/api/admin/user/login",
            "/api/c/user/login",
            "/api/c/activity/**",
            // 支付渠道异步通知（由渠道验签守卫，无用户 JWT）
            "/api/c/pay/callback"
    );

    /** 共享路径匹配器（AntPathMatcher 线程安全，全局单例复用） */
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public static boolean isWhiteListed(String path) {
        return WHITELIST.stream().anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    /** 是否管理端路径 */
    public static boolean isAdminPath(String path) {
        return MATCHER.match(ADMIN_PATH, path);
    }
}
