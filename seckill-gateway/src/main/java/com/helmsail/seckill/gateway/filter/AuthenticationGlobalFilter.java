package com.helmsail.seckill.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.common.jwt.JwtClaims;
import com.helmsail.seckill.common.jwt.JwtUtils;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.common.tracing.BaggageKeys;
import com.helmsail.seckill.common.user.Role;
import com.helmsail.seckill.gateway.auth.GatewayAuth;
import com.helmsail.seckill.gateway.exception.GatewayError;
import com.helmsail.seckill.gateway.exception.GatewayResponseWriter;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 鉴权过滤器
 *
 * JWT 校验（一次解析）→ 提取 userId/role → 移除 Authorization → 设置可信 userId；
 * role 写入 exchange 属性，供 AuthorizationGlobalFilter 做授权校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单跳过鉴权
        if (GatewayAuth.isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 解析 JWT
        String authHeader = request.getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, GatewayError.UNAUTHORIZED, "缺少认证令牌");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        String userId;
        Role role;
        try {
            Claims claims = jwtUtils.parseToken(token);
            userId = claims.get(JwtClaims.USER_ID, String.class);
            // JWT 中角色为字符串码，未知值解析为 null（授权时按安全默认拒绝）
            role = Role.fromCode(claims.get(JwtClaims.ROLE, String.class));
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return unauthorized(exchange, GatewayError.UNAUTHORIZED, "认证令牌无效");
        }

        if (userId == null || userId.isEmpty()) {
            return unauthorized(exchange, GatewayError.UNAUTHORIZED, "令牌中缺少用户信息");
        }

        // 认证上下文：角色放入 exchange 属性，供授权过滤器读取
        exchange.getAttributes().put(GatewayAuth.ATTR_ROLE, role);

        // 清理 + 设置 userId（键名以 common 的 BaggageKeys 为准，与后端 HttpBaggageFilter 一致）
        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_AUTHORIZATION);
                    headers.remove(BaggageKeys.USER_ID);
                    headers.add(BaggageKeys.USER_ID, userId);
                })
                .build();

        // 写入 Reactor Context：配合 enableAutomaticContextPropagation，自动同步到 MDC 供日志输出
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .contextWrite(ctx -> ctx.put(BaggageKeys.USER_ID, userId));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, GatewayError error, String message) {
        return GatewayResponseWriter.write(exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                Result.of(error.getCode(), message));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
