package com.jinmo.aiwriting.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.Map;

@ConfigurationProperties(prefix = "openrouter")
@Validated
public record OpenRouterProperties(
    @NotEmpty String apiKey,
    @NotEmpty String baseUrl,
    @NotEmpty String model,
    Map<String, String> headers,
    Double temperature,
    Integer maxTokens
) {
    public OpenRouterProperties {
        if (temperature == null) temperature = 0.7;
        if (maxTokens == null) maxTokens = 2048;
    }
} 