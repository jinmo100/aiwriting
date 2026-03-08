package com.jinmo.aiwriting.domain.dto;

import com.jinmo.aiwriting.domain.entity.ApiConfig;
import java.time.LocalDateTime;

/**
 * API配置响应DTO
 */
public record ApiConfigResponse(
    Long id,
    String configName,
    String provider,
    String baseUrl,
    String modelName,
    Boolean isDefault,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ApiConfigResponse fromEntity(ApiConfig config) {
        return new ApiConfigResponse(
            config.getId(),
            config.getConfigName(),
            config.getProvider(),
            config.getBaseUrl(),
            config.getModelName(),
            config.getIsDefault(),
            config.getCreatedAt(),
            config.getUpdatedAt()
        );
    }
}
