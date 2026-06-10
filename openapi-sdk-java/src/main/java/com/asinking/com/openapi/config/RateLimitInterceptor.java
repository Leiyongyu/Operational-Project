package com.asinking.com.openapi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 限流拦截器：基于 Bucket4j 令牌桶算法。
 * - 登录接口：每分钟最多 10 次（防暴力破解）
 * - 普通 API：每分钟最多 60 次
 * - 领星 API Key 接口：每分钟最多 30 次
 * 按客户端 IP 地址区分桶。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> apiBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> lingxingBuckets = new ConcurrentHashMap<>();

    private static final int LOGIN_LIMIT_PER_MINUTE = 10;
    private static final int API_LIMIT_PER_MINUTE = 60;
    private static final int LINGXING_LIMIT_PER_MINUTE = 30;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        String clientIp = getClientIp(request);

        Bucket bucket;
        int limit;

        if (path.contains("/api/user/login")) {
            bucket = loginBuckets.computeIfAbsent(clientIp, k -> createBucket(LOGIN_LIMIT_PER_MINUTE));
            limit = LOGIN_LIMIT_PER_MINUTE;
        } else if (path.contains("/api/lingxing/")) {
            bucket = lingxingBuckets.computeIfAbsent(clientIp, k -> createBucket(LINGXING_LIMIT_PER_MINUTE));
            limit = LINGXING_LIMIT_PER_MINUTE;
        } else {
            bucket = apiBuckets.computeIfAbsent(clientIp, k -> createBucket(API_LIMIT_PER_MINUTE));
            limit = API_LIMIT_PER_MINUTE;
        }

        if (bucket.tryConsume(1)) {
            return true;
        }

        LOG.warn("限流触发: IP={} path={} limit={}/min", clientIp, path, limit);
        response.setStatus(429); // Too Many Requests
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试（限" + limit + "次/分钟）\"}");
        return false;
    }

    private Bucket createBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1))))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) {
            return xri;
        }
        return request.getRemoteAddr();
    }
}
