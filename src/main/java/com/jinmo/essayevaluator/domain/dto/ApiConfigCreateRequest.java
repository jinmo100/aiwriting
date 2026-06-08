package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.ai.provider.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API 配置创建请求。创建时 API Key 必填。
 */
public record ApiConfigCreateRequest(
    @NotBlank(message = "配置名称不能为空")
    String configName,

    @NotNull(message = "提供商类型不能为空")
    ProviderType providerType,

    String providerLabel,

    @NotBlank(message = "API基础URL不能为空")
    String baseUrl,

    @NotBlank(message = "API Key不能为空")
    String apiKey,

    @NotBlank(message = "模型名称不能为空")
    String modelName,

    Double temperature,
    Integer maxTokens,
    Integer timeoutSeconds,
    String modelParametersJson,
    Double inputTokenPricePerMillion,
    Double outputTokenPricePerMillion,
    String currency,
    Boolean isDefault
) {
}
