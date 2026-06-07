package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.ai.provider.AIProviderErrorCode;
import com.jinmo.essayevaluator.ai.provider.ProviderType;

public record ProviderTestResponse(
    Boolean success,
    ProviderType providerType,
    String modelName,
    Long latencyMillis,
    String message,
    AIProviderErrorCode errorCode,
    Boolean jsonValid,
    Boolean schemaValid
) {
    public static ProviderTestResponse success(ProviderType providerType, String modelName, Long latencyMillis, String message) {
        return new ProviderTestResponse(true, providerType, modelName, latencyMillis, message, null, null, null);
    }

    public static ProviderTestResponse structuredSuccess(ProviderType providerType, String modelName, Long latencyMillis) {
        return new ProviderTestResponse(true, providerType, modelName, latencyMillis, "结构化输出测试成功", null, true, true);
    }

    public static ProviderTestResponse failure(ProviderType providerType, String modelName, Long latencyMillis, String message, AIProviderErrorCode errorCode) {
        return new ProviderTestResponse(false, providerType, modelName, latencyMillis, message, errorCode, false, false);
    }
}
