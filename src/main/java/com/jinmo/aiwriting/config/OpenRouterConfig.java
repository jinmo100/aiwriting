package com.jinmo.aiwriting.config;

import com.jinmo.aiwriting.service.ai.OpenRouterClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableConfigurationProperties(OpenRouterProperties.class)
public class OpenRouterConfig {

    @Bean
    public OpenRouterClient openRouterClient(
        OpenRouterProperties properties,
        RetryTemplate retryTemplate
    ) {
        return new OpenRouterClient(
            properties.apiKey(),
            properties.baseUrl(),
            properties.model(),
            properties.headers(),
            retryTemplate
        );
    }
} 