package com.helmsail.seckill.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.gateway.result.GatewayError;
import com.helmsail.seckill.gateway.result.Result;
import com.helmsail.seckill.gateway.util.JwtUtils;
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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 鉴权过滤器
 *
 * JWT 校验 → 提取 userId → 移除 Authorization → 设置可信 userId
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
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** 白名单：无需 JWT 校验的路径 */
    private static final String[] WHITE_LIST = {
            "/api/admin/user/login",
            "/api/c/activity/**"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单跳过鉴权
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // 解析 JWT
        String authHeader = request.getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, GatewayError.UNAUTHORIZED, "缺少认证令牌");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        String userId;
        try {
            userId = jwtUtils.getUserId(token);
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return unauthorized(exchange, GatewayError.UNAUTHORIZED, "认证令牌无效");
        }

        if (userId == null || userId.isEmpty()) {
            return unauthorized(exchange, GatewayError.UNAUTHORIZED, "令牌中缺少用户信息");
        }

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

    private boolean isWhiteListed(String path) {
        for (String pattern : WHITE_LIST) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, GatewayError error, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = mapper.writeValueAsBytes(Result.fail(error, message));
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
