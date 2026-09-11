package com.helmsail.seckill.gateway.auth;

import org.springframework.util.AntPathMatcher;

/**
 * 网关认证共享常量与白名单
 *
 * 供 AuthenticationGlobalFilter / AuthorizationGlobalFilter 共用。
 */
public final class GatewayAuth {

    private GatewayAuth() {}

    /** exchange 属性键：认证过滤器写入的角色，供授权过滤器读取 */
    public static final String ATTR_ROLE = "gateway.auth.role";

    /** 运营角色值（与 t_user.role 对齐：0=C端用户，1=运营人员） */
    public static final String ROLE_OPERATOR = "1";

    /** 免认证路径 */
    private static final String[] WHITELIST = {
            "/api/admin/user/login",
            "/api/c/activity/**"
    };

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public static boolean isWhiteListed(String path) {
        for (String pattern : WHITELIST) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
