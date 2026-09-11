package com.helmsail.seckill.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.gateway.auth.GatewayAuth;
import com.helmsail.seckill.gateway.result.GatewayError;
import com.helmsail.seckill.gateway.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
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

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String ADMIN_PATH = "/api/admin/**";

    private final ObjectMapper mapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (PATH_MATCHER.match(ADMIN_PATH, path) && !GatewayAuth.isWhiteListed(path)) {
            String role = exchange.getAttribute(GatewayAuth.ATTR_ROLE);
            if (!GatewayAuth.ROLE_OPERATOR.equals(role)) {
                log.warn("无权限访问: path={}, role={}", path, role);
                return forbidden(exchange);
            }
        }

        return chain.filter(exchange);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = mapper.writeValueAsBytes(Result.fail(GatewayError.FORBIDDEN, "无权限访问"));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
