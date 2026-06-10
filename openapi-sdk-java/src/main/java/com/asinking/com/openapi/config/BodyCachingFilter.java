package com.asinking.com.openapi.config;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 请求体缓存过滤器：将 HttpServletRequest 包装为 ContentCachingRequestWrapper，
 * 使得拦截器（如 GoodcangSignatureInterceptor）和 Controller 可以多次读取请求体。
 */
@Component
public class BodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 仅对谷仓回调接口启用缓存（减少不必要的内存开销）
        if (request.getRequestURI().contains("/api/goodcang/callback")) {
            ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
            filterChain.doFilter(wrapper, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
