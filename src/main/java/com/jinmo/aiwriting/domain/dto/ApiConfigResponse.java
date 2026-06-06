package com.jinmo.aiwriting.domain.dto;

import com.jinmo.aiwriting.domain.entity.ApiConfig;
import java.time.LocalDateTime;

/**
 * API配置响应DTO
 */
public record ApiConfigResponse(
    Long id,
    String configName,
    String providerType,
    String providerLabel,
    String baseUrl,
    String modelName,
    Double temperature,
    Integer maxTokens,
    Integer timeoutSeconds,
    String modelParametersJson,
    Boolean isDefault,
    Boolean hasApiKey,
    String apiKeyPreview,
    String lastTestStatus,
    String lastTestErrorCode,
    String lastTestMessage,
    Integer lastTestLatencyMs,
    LocalDateTime lastTestedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ApiConfigResponse fromEntity(ApiConfig config) {
        return fromEntity(config, config.getApiKey());
    }

    public static ApiConfigResponse fromEntity(ApiConfig config, String plainApiKeyForPreview) {
        return new ApiConfigResponse(
            config.getId(),
            config.getConfigName(),
            config.getProviderType() != null ? config.getProviderType().value() : null,
            config.getProviderLabel(),
            config.getBaseUrl(),
            config.getModelName(),
            config.getTemperature(),
            config.getMaxTokens(),
            config.getTimeoutSeconds(),
            config.getModelParametersJson(),
            config.getIsDefault(),
            hasApiKey(config),
            previewApiKey(plainApiKeyForPreview),
            config.getLastTestStatus(),
            config.getLastTestErrorCode(),
            config.getLastTestMessage(),
            config.getLastTestLatencyMs(),
            config.getLastTestedAt(),
            config.getCreatedAt(),
            config.getUpdatedAt()
        );
    }

    private static boolean hasApiKey(ApiConfig config) {
        return (config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isBlank())
            || (config.getApiKey() != null && !config.getApiKey().isBlank());
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
