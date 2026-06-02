package com.asinking.com.openapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 文档配置，Swagger UI 访问路径：/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jmhOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("JMH 跨境电商中台系统 API")
                        .description("集成领星 ERP、谷仓 WMS、飞书等第三方平台，提供库存管理、采购计划、销量统计、利润分析等功能")
                        .version("1.0")
                        .contact(new Contact().name("JMH Team")));
    }
}
