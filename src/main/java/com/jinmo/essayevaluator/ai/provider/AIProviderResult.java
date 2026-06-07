package com.jinmo.essayevaluator.ai.provider;

/**
 * Provider 通用文本生成结果，避免业务层直接依赖 LangChain4j 类型。
 */
public record AIProviderResult(
    String text,
    String rawResponse,
    String providerRequestId,
    String modelName,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    Long latencyMillis,
    ProviderType providerType
) {
}
