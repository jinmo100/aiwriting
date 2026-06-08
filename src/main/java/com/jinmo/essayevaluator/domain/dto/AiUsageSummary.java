package com.jinmo.essayevaluator.domain.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiUsageSummary(
    String provider,
    String endpointType,
    String model,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    Integer latencyMs,
    String usageSource,
    BigDecimal estimatedCost,
    String currency,
    Integer invocationCount,
    List<AiInvocationLogResponse> invocations
) {
}
