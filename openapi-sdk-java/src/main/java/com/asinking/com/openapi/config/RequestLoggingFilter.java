package com.asinking.com.openapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * 请求日志过滤器：记录每次 API 调用的方法、URI、耗时、状态码。
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws java.io.IOException, jakarta.servlet.ServletException {
        long start = System.currentTimeMillis();
        ContentCachingRequestWrapper reqWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper respWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(reqWrapper, respWrapper);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            int status = response.getStatus();

            // 读取请求体，脱敏后输出
            String reqBody = "";
            byte[] reqBytes = reqWrapper.getContentAsByteArray();
            if (reqBytes.length > 0) {
                reqBody = new String(reqBytes, StandardCharsets.UTF_8);
                // 脱敏：password/token/secret 等敏感字段替换为 ***
                reqBody = reqBody.replaceAll("\"(password|token|secret|appSecret|apiKey|Authorization)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"***\"");
                if (reqBody.length() > 500) {
                    reqBody = reqBody.substring(0, 500) + "...";
                }
            }

            String path = query != null ? uri + "?" + query : uri;
            LOG.info("{} {} -> {} ({}ms){}",
                    method, path, status, elapsed,
                    reqBody.isEmpty() ? "" : " body=" + reqBody);

            respWrapper.copyBodyToResponse();
        }
    }
}
