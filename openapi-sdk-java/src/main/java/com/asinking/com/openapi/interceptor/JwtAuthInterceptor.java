package com.asinking.com.openapi.interceptor;

import com.asinking.com.openapi.config.TokenBlacklist;
import com.asinking.com.openapi.utils.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * JWT 认证拦截器：拦截 /api/**（排除 /api/user/login），解析 Bearer token 并注入请求属性。
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String ATTR_USER_ID = "jwt.uid";
    public static final String ATTR_ACCOUNT = "jwt.account";
    public static final String ATTR_ROLE = "jwt.role";
    public static final String ATTR_JTI = "jwt.jti";

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthInterceptor(JwtTokenService jwtTokenService, TokenBlacklist tokenBlacklist) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractBearerToken(request.getHeader(HEADER_AUTHORIZATION));
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "Missing token");
            return false;
        }

        try {
            Jws<Claims> jws = jwtTokenService.parse(token);
            Claims claims = jws.getPayload();
            String jti = claims.getId();
            if (tokenBlacklist.isRevoked(jti)) {
                writeUnauthorized(response, "Token revoked");
                return false;
            }

            request.setAttribute(ATTR_JTI, jti);
            request.setAttribute(ATTR_ACCOUNT, claims.getSubject());
            request.setAttribute(ATTR_ROLE, String.valueOf(claims.get("role")));
            request.setAttribute(ATTR_USER_ID, String.valueOf(claims.get("uid")));
            return true;
        } catch (Exception e) {
            writeUnauthorized(response, "Invalid token");
            return false;
        }
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}

