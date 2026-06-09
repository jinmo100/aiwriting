package com.jinmo.essayevaluator.embedding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Embedding 配置与连接测试请求 DTO。
 */
public final class EmbeddingRequestDtos {

    private EmbeddingRequestDtos() {
    }

    /**
     * 创建 Embedding 配置。创建时 API Key 必填。
     */
    public record EmbeddingConfigCreateRequest(
        @NotBlank(message = "配置名称不能为空")
        String configName,

        @NotBlank(message = "Embedding Provider 类型不能为空")
        String providerType,

        @NotBlank(message = "Embedding API 基础 URL 不能为空")
        String baseUrl,

        @NotBlank(message = "Embedding API Key 不能为空")
        String apiKey,

        @NotBlank(message = "Embedding 模型名称不能为空")
        String modelName,

        @NotNull(message = "Embedding 维度不能为空")
        Integer dimensions,

        Integer timeoutSeconds,
        Boolean isDefault
    ) {
    }

    /**
     * 更新 Embedding 配置。apiKey 为空表示保留旧 Key。
     */
    public record EmbeddingConfigUpdateRequest(
        @NotBlank(message = "配置名称不能为空")
        String configName,

        @NotBlank(message = "Embedding Provider 类型不能为空")
        String providerType,

        @NotBlank(message = "Embedding API 基础 URL 不能为空")
        String baseUrl,

        String apiKey,

        @NotBlank(message = "Embedding 模型名称不能为空")
        String modelName,

        @NotNull(message = "Embedding 维度不能为空")
        Integer dimensions,

        Integer timeoutSeconds,
        Boolean isDefault
    ) {
    }

    /**
     * 未保存 Embedding 配置连接测试请求。
     */
    public record EmbeddingConfigTestRequest(
        @NotBlank(message = "Embedding Provider 类型不能为空")
        String providerType,

        @NotBlank(message = "Embedding API 基础 URL 不能为空")
        String baseUrl,

        @NotBlank(message = "Embedding API Key 不能为空")
        String apiKey,

        @NotBlank(message = "Embedding 模型名称不能为空")
        String modelName,

        @NotNull(message = "Embedding 维度不能为空")
        Integer dimensions,

        Integer timeoutSeconds
    ) {
    }
}
