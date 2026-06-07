package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.ai.provider.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * API 配置更新请求。apiKey 为空表示保留旧 Key。
 */
public record ApiConfigUpdateRequest(
    @NotBlank(message = "配置名称不能为空")
    String configName,

    @NotNull(message = "提供商类型不能为空")
    ProviderType providerType,

    String providerLabel,

    @NotBlank(message = "API基础URL不能为空")
    String baseUrl,

    String apiKey,

    @NotBlank(message = "模型名称不能为空")
    String modelName,

    Double temperature,
    Integer maxTokens,
    Integer timeoutSeconds,
    String modelParametersJson,
    Boolean isDefault
) {
}
