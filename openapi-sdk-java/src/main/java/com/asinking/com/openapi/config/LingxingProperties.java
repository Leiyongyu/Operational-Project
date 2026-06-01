package com.asinking.com.openapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lingxing")
/** 领星 OpenAPI 配置属性：endpoint、appId、appSecret、超时时间、库存仓库 ID 列表。 */
public class LingxingProperties {

    private String endpoint;
    private String appId;
    private String appSecret;
    private int connectTimeout = 30000;
    private int readTimeout = 30000;
    private int tokenRefreshSkewSeconds = 300;
    private String inventoryWids;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getTokenRefreshSkewSeconds() {
        return tokenRefreshSkewSeconds;
    }

    public void setTokenRefreshSkewSeconds(int tokenRefreshSkewSeconds) {
        this.tokenRefreshSkewSeconds = tokenRefreshSkewSeconds;
    }

    public String getInventoryWids() {
        return inventoryWids;
    }

    public void setInventoryWids(String inventoryWids) {
        this.inventoryWids = inventoryWids;
    }
}
