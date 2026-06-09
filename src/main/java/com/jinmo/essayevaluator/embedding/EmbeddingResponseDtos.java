package com.jinmo.essayevaluator.embedding;

import com.jinmo.essayevaluator.ai.provider.AIProviderErrorCode;

import java.time.LocalDateTime;

/**
 * Embedding 配置与连接测试响应 DTO。
 */
public final class EmbeddingResponseDtos {

    private EmbeddingResponseDtos() {
    }

    public record EmbeddingConfigResponse(
        Long id,
        String configName,
        String providerType,
        String baseUrl,
        String modelName,
        Integer dimensions,
        Boolean isDefault,
        Boolean hasApiKey,
        String apiKeyPreview,
        String lastTestStatus,
        String lastTestMessage,
        Integer lastTestLatencyMs,
        LocalDateTime lastTestedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        public static EmbeddingConfigResponse fromEntity(EmbeddingConfig config, String plainApiKeyForPreview) {
            return new EmbeddingConfigResponse(
                config.getId(),
                config.getConfigName(),
                config.getProviderType() != null ? config.getProviderType().value() : null,
                config.getBaseUrl(),
                config.getModelName(),
                config.getDimensions(),
                config.getIsDefault(),
                config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isBlank(),
                previewApiKey(plainApiKeyForPreview),
                config.getLastTestStatus(),
                config.getLastTestMessage(),
                config.getLastTestLatencyMs(),
                config.getLastTestedAt(),
                config.getCreatedAt(),
                config.getUpdatedAt()
            );
        }
    }

    public record EmbeddingTestResponse(
        Boolean success,
        String providerType,
        String modelName,
        Integer dimensions,
        Long latencyMillis,
        String message,
        AIProviderErrorCode errorCode
    ) {
        public static EmbeddingTestResponse success(EmbeddingConfig config, long latencyMillis) {
            return new EmbeddingTestResponse(
                true,
                config.getProviderType().value(),
                config.getModelName(),
                config.getDimensions(),
                latencyMillis,
                "Embedding 连接成功",
                null
            );
        }

        public static EmbeddingTestResponse failure(
            EmbeddingConfig config,
            long latencyMillis,
            String message,
            AIProviderErrorCode errorCode
        ) {
            return new EmbeddingTestResponse(
                false,
                config.getProviderType().value(),
                config.getModelName(),
                config.getDimensions(),
                latencyMillis,
                message,
                errorCode
            );
        }
    }

    private static String previewApiKey(String plainApiKey) {
        if (plainApiKey == null || plainApiKey.isBlank()) {
            return null;
        }
        String trimmed = plainApiKey.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, Math.min(3, trimmed.length())) + "..." + trimmed.substring(trimmed.length() - 4);
    }
}
