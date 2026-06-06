package com.jinmo.aiwriting.domain.dto;

import com.jinmo.aiwriting.ai.provider.ProviderModelInfo;

import java.util.List;

public record ProviderModelsResponse(
    Boolean success,
    Boolean fromCache,
    List<ProviderModelInfo> models,
    Long latencyMillis
) {
}
