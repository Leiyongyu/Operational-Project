package com.asinking.com.openapi.config;

import com.asinking.com.openapi.security.ApiKeyInterceptor;
import com.asinking.com.openapi.interceptor.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置：CORS 跨域、JWT 认证拦截器（/api/**）、API Key 拦截器（/api/lingxing/**）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FrontendAuthProperties frontendAuthProperties;
    private final ApiKeyInterceptor apiKeyInterceptor;
    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebMvcConfig(FrontendAuthProperties frontendAuthProperties, ApiKeyInterceptor apiKeyInterceptor, JwtAuthInterceptor jwtAuthInterceptor) {
        this.frontendAuthProperties = frontendAuthProperties;
        this.apiKeyInterceptor = apiKeyInterceptor;
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = frontendAuthProperties.getAllowedOrigins();
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/user/login", "/api/lingxing/**", "/api/goodcang/**");
        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns("/api/lingxing/**");
    }
}
