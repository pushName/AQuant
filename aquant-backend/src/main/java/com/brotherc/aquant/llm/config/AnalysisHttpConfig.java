package com.brotherc.aquant.llm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** 分析服务 HTTP 客户端配置。 */
@Configuration
public class AnalysisHttpConfig {
    @Bean
    public RestTemplate analysisRestTemplate() {
        return new RestTemplate();
    }
}
