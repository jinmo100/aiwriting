package com.jinmo.essayevaluator.ai.provider;

import com.jinmo.essayevaluator.domain.entity.ApiConfig;

/**
 * AI Provider 通用适配器。
 */
public interface AIProviderAdapter {

    ProviderType providerType();

    AIProviderResult generate(AIProviderRequest request, ApiConfig config);

    default boolean supportsStreaming() {
        return false;
    }
}
