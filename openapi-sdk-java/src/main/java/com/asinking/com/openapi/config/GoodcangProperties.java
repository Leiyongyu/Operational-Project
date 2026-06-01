package com.asinking.com.openapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "goodcang")
/** 谷仓 WMS API 配置属性：appToken、appKey（Basic Auth 凭证）。 */
public class GoodcangProperties {
    private String appToken;
    private String appKey;

    public String getAppToken() { return appToken; }
    public void setAppToken(String appToken) { this.appToken = appToken; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
}
