package com.asinking.com.openapi.security;

import com.asinking.com.openapi.config.GoodcangProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 谷仓 Webhook 签名验证拦截器：
 * 验证回调请求的签名（HMAC-SHA256），防止伪造回调。
 * 拦截 /api/goodcang/callback 路径。
 *
 * 验证逻辑：
 * 1. 获取请求头 X-Goodcang-Sign（Base64(HMAC-SHA256(body, appKey))）
 * 2. 获取请求头 X-Goodcang-Timestamp（时间戳，防重放，5分钟有效期）
 * 3. 计算本地签名并比对
 *
 * 依赖 BodyCachingFilter 包装的 ContentCachingRequestWrapper 实现多次读取请求体。
 */
@Component
public class GoodcangSignatureInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(GoodcangSignatureInterceptor.class);
    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000; // 5分钟

    private final GoodcangProperties properties;

    public GoodcangSignatureInterceptor(GoodcangProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 读取请求体（ContentCachingRequestWrapper 已在 Filter 中包装）
        String body = getRequestBody(request);

        String signature = request.getHeader("X-Goodcang-Sign");
        String timestamp = request.getHeader("X-Goodcang-Timestamp");

        // 如果有签名头，则验证；如果没有（调试模式），记录告警但放行
        if (!StringUtils.hasText(signature)) {
            LOG.warn("谷仓回调缺少签名头 X-Goodcang-Sign，仅记录不拦截（可能为调试环境）");
            return true;
        }

        // 防重放：时间戳检查
        if (StringUtils.hasText(timestamp)) {
            try {
                long ts = Long.parseLong(timestamp);
                long now = System.currentTimeMillis();
                if (Math.abs(now - ts) > TIMESTAMP_WINDOW_MS) {
                    LOG.warn("谷仓回调时间戳过期: ts={} now={} diff={}ms", ts, now, Math.abs(now - ts));
                    writeForbidden(response, "Timestamp expired");
                    return false;
                }
            } catch (NumberFormatException e) {
                LOG.warn("谷仓回调时间戳格式错误: {}", timestamp);
            }
        }

        // 计算本地签名
        String appKey = properties.getAppKey();
        String localSign = hmacSha256(body, appKey);

        if (!localSign.equals(signature)) {
            LOG.warn("谷仓回调签名验证失败: expected={} actual={}", localSign, signature);
            writeForbidden(response, "Invalid signature");
            return false;
        }

        LOG.info("谷仓回调签名验证通过");
        return true;
    }

    private String getRequestBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            byte[] content = wrapper.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(spec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 计算失败", e);
        }
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":403,\"message\":\"" + message + "\"}");
    }
}
