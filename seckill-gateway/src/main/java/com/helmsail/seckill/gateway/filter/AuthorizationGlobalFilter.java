package com.helmsail.seckill.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.common.user.Role;
import com.helmsail.seckill.gateway.auth.GatewayAuth;
import com.helmsail.seckill.gateway.exception.GatewayError;
import com.helmsail.seckill.gateway.exception.GatewayResponseWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 授权过滤器
 *
 * 按路径校验角色：/api/admin/** 要求运营角色（role=1）。
 * 认证上下文由 AuthenticationGlobalFilter 写入；白名单路径直接放行，
 * 角色缺失（如旧版令牌）按安全默认拒绝。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationGlobalFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (GatewayAuth.isAdminPath(path) && !GatewayAuth.isWhiteListed(path)) {
            Role role = exchange.getAttribute(GatewayAuth.ATTR_ROLE);
            if (GatewayAuth.ADMIN_ROLE != role) {
                log.warn("无权限访问: path={}, role={}", path, role);
                return GatewayResponseWriter.write(exchange, objectMapper, HttpStatus.FORBIDDEN,
                        Result.of(GatewayError.FORBIDDEN.getCode(), "无权限访问"));
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
