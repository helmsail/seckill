package com.helmsail.seckill.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.common.jwt.JwtClaims;
import com.helmsail.seckill.common.jwt.JwtUtils;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.gateway.auth.GatewayAuth;
import com.helmsail.seckill.gateway.exception.GatewayError;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
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

    private static final String HEADER_USER_ID = "userId";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final ObjectMapper mapper;

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
        String role;
        try {
            Claims claims = jwtUtils.parseToken(token);
            userId = claims.get(JwtClaims.USER_ID, String.class);
            role = claims.get(JwtClaims.ROLE, String.class);
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return unauthorized(exchange, GatewayError.UNAUTHORIZED, "认证令牌无效");
        }

        if (userId == null || userId.isEmpty()) {
            return unauthorized(exchange, GatewayError.UNAUTHORIZED, "令牌中缺少用户信息");
        }

        // 认证上下文：角色放入 exchange 属性，供授权过滤器读取
        exchange.getAttributes().put(GatewayAuth.ATTR_ROLE, role);

        // 清理 + 设置 userId
        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_AUTHORIZATION);
                    headers.remove(HEADER_USER_ID);
                    headers.add(HEADER_USER_ID, userId);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, GatewayError error, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = mapper.writeValueAsBytes(Result.of(error.getCode(), message));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
