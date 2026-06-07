package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.ai.provider.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 未保存配置时临时拉取模型列表的请求。
 */
public record ProviderModelsFetchRequest(
    @NotNull(message = "提供商类型不能为空")
    ProviderType providerType,

    @NotBlank(message = "API基础URL不能为空")
    String baseUrl,

    @NotBlank(message = "API Key不能为空")
    String apiKey,

    Boolean forceRefresh
) {
}
