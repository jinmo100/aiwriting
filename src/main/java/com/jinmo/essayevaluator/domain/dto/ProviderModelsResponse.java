package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.ai.provider.ProviderModelInfo;

import java.util.List;

public record ProviderModelsResponse(
    Boolean success,
    Boolean fromCache,
    List<ProviderModelInfo> models,
    Long latencyMillis
) {
}
