package com.jinmo.aiwriting.ai.provider;

import java.util.Map;

/**
 * Provider 通用文本生成请求。
 */
public record AIProviderRequest(
    String systemPrompt,
    String userPrompt,
    String responseSchemaName,
    String responseSchemaJson,
    Map<String, Object> requestOptions
) {
    public AIProviderRequest {
        requestOptions = requestOptions == null ? Map.of() : Map.copyOf(requestOptions);
    }
}
