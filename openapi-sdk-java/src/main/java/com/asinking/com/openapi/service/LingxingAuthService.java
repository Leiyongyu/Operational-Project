package com.asinking.com.openapi.service;

import com.asinking.com.openapi.config.LingxingProperties;
import com.asinking.com.openapi.sdk.core.Config;
import com.asinking.com.openapi.sdk.entity.Result;
import com.asinking.com.openapi.sdk.okhttp.AKRestClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 领星认证服务：管理 access_token 的获取、缓存、自动刷新。
 * 使用 double-check locking 避免并发时重复请求 token。
 */
@Service
public class LingxingAuthService {

    private final LingxingProperties properties;
    private final ObjectMapper objectMapper;
    private final Object tokenLock = new Object();
    private volatile CachedToken cachedToken;

    /** 构造认证服务，注入配置及 JSON 工具 */
    public LingxingAuthService(LingxingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 调用 SDK 获取 access_token 原始响应 */
    public Result<?> getAccessTokenResponse() throws Exception {
        AKRestClient client = new AKRestClient(properties.getEndpoint(), buildConfig());
        Result<?> result = client.getAccessToken(properties.getAppId(), properties.getAppSecret());
        if (result == null) {
            throw new IllegalStateException("领星 access_token 响应为空");
        }
        return result;
    }

    /** 获取有效 access_token，双重检查锁避免并发重复请求，skewSeconds 提前刷新防止过期 */
    public String getAccessToken() throws Exception {
        CachedToken token = cachedToken;
        if (token != null && token.isValid(properties.getTokenRefreshSkewSeconds())) {
            return token.accessToken;
        }

        synchronized (tokenLock) {
            token = cachedToken;
            if (token != null && token.isValid(properties.getTokenRefreshSkewSeconds())) {
                return token.accessToken;
            }

            CachedToken updated = refreshOrFetchToken(token);
            cachedToken = updated;
            return updated.accessToken;
        }
    }

    private Config buildConfig() {
        return new Config()
                .withConnectionTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());
    }

    private String extractAccessToken(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof String) {
            return (String) data;
        }

        Map<String, Object> map = objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {
        });
        String accessToken = firstNonBlank(map.get("access_token"), map.get("accessToken"), map.get("token"));
        if (StringUtils.hasText(accessToken)) {
            return accessToken;
        }
        Object nestedData = map.get("data");
        return nestedData == null ? null : extractAccessToken(nestedData);
    }

    private CachedToken refreshOrFetchToken(CachedToken existing) throws Exception {
        if (existing != null && StringUtils.hasText(existing.refreshToken)) {
            CachedToken refreshed = tryRefresh(existing.refreshToken);
            if (refreshed != null && refreshed.isValid(0)) {
                return refreshed;
            }
        }
        return fetchByAppSecret();
    }

    private CachedToken fetchByAppSecret() throws Exception {
        Result<?> result = getAccessTokenResponse();
        if (!isSuccessCode(result.getCode())) {
            throw new IllegalStateException("获取 access_token 失败: code=" + result.getCode() + ", msg=" + result.getMsg());
        }
        CachedToken token = extractToken(result.getData());
        if (token == null || !StringUtils.hasText(token.accessToken)) {
            throw new IllegalStateException("无法从 access_token 响应中解析 token: code=" + result.getCode() + ", msg=" + result.getMsg());
        }
        return token;
    }

    private CachedToken tryRefresh(String refreshToken) {
        try {
            AKRestClient client = new AKRestClient(properties.getEndpoint(), buildConfig());
            Object response = client.refreshToken(properties.getAppId(), refreshToken);
            if (response == null) {
                return null;
            }
            Map<String, Object> map = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {
            });
            Object code = map.get("code");
            Object msg = map.get("msg");
            Object data = map.get("data");
            if (!isSuccessCode(code)) {
                return null;
            }
            CachedToken token = extractToken(data);
            if (token == null || !StringUtils.hasText(token.accessToken)) {
                return null;
            }
            return token;
        } catch (Exception ignored) {
            return null;
        }
    }

    private CachedToken extractToken(Object data) {
        if (data == null) {
            return null;
        }
        Map<String, Object> map = objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {
        });

        String accessToken = firstNonBlank(map.get("access_token"), map.get("accessToken"), map.get("token"));
        String refreshToken = firstNonBlank(map.get("refresh_token"), map.get("refreshToken"));
        long expiresInSeconds = parseLong(firstNonBlank(map.get("expires_in"), map.get("expiresIn")), 0L);

        long now = System.currentTimeMillis();
        long expiresAtMillis = expiresInSeconds > 0 ? now + expiresInSeconds * 1000L : now;

        CachedToken token = new CachedToken();
        token.accessToken = accessToken;
        token.refreshToken = refreshToken;
        token.expiresAtMillis = expiresAtMillis;
        token.raw = new LinkedHashMap<>(map);
        return token;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private boolean isSuccessCode(Object code) {
        if (code == null) {
            return false;
        }
        String text = String.valueOf(code);
        return "0".equals(text) || "200".equals(text) || "OK".equalsIgnoreCase(text) || "SUCCESS".equalsIgnoreCase(text);
    }

    private long parseLong(String value, long defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static final class CachedToken {
        private String accessToken;
        private String refreshToken;
        private long expiresAtMillis;
        private Map<String, Object> raw;

        private boolean isValid(int skewSeconds) {
            if (!StringUtils.hasText(accessToken)) {
                return false;
            }
            long skewMillis = Math.max(0, skewSeconds) * 1000L;
            return System.currentTimeMillis() + skewMillis < expiresAtMillis;
        }
    }
}
