package com.jinmo.essayevaluator.ai.provider;

public record ChatModelCacheKey(
    ProviderType providerType,
    String baseUrl,
    String modelName,
    String apiKeyHash,
    Double temperature,
    Integer maxTokens,
    Integer timeoutSeconds,
    String modelParametersHash
) {
}
