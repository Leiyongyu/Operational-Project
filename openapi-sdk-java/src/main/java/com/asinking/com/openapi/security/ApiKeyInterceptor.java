package com.asinking.com.openapi.security;

import com.asinking.com.openapi.config.FrontendAuthProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * API Key 拦截器：拦截 /api/lingxing/**，校验 X-Client-Id 和 X-Api-Key 请求头。
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER_CLIENT_ID = "X-Client-Id";
    public static final String HEADER_API_KEY = "X-Api-Key";

    private final FrontendAuthProperties frontendAuthProperties;

    public ApiKeyInterceptor(FrontendAuthProperties frontendAuthProperties) {
        this.frontendAuthProperties = frontendAuthProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String clientId = request.getHeader(HEADER_CLIENT_ID);
        String apiKey = request.getHeader(HEADER_API_KEY);

        if (isValid(clientId, apiKey)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":401,\"message\":\"Invalid API key or client id\"}");
        return false;
    }

    private boolean isValid(String clientId, String apiKey) {
        return StringUtils.hasText(clientId)
                && StringUtils.hasText(apiKey)
                && clientId.equals(frontendAuthProperties.getClientId())
                && apiKey.equals(frontendAuthProperties.getApiKey());
    }
}
