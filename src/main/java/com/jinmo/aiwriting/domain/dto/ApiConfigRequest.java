package com.jinmo.aiwriting.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * API配置请求DTO
 */
public record ApiConfigRequest(
    @NotBlank(message = "配置名称不能为空")
    String configName,

    @NotBlank(message = "提供商不能为空")
    String provider,

    @NotBlank(message = "API基础URL不能为空")
    String baseUrl,

    @NotBlank(message = "API Key不能为空")
    String apiKey,

    @NotBlank(message = "模型名称不能为空")
    String modelName,

    Boolean isDefault
) {
}
