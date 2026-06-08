package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.domain.entity.AiInvocationLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AiInvocationLogResponse(
    Long id,
    String purpose,
    String provider,
    String endpointType,
    String model,
    String providerRequestId,
    String status,
    Integer latencyMs,
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    String usageSource,
    BigDecimal estimatedCost,
    String currency,
    String failureCode,
    String failureMessage,
    LocalDateTime createdAt
) {
    public static AiInvocationLogResponse fromEntity(AiInvocationLog log) {
        return new AiInvocationLogResponse(
            log.getId(),
            log.getPurpose(),
            log.getProvider(),
            log.getEndpointType(),
            log.getModel(),
            log.getProviderRequestId(),
            log.getStatus(),
            log.getLatencyMs(),
            log.getInputTokens(),
            log.getOutputTokens(),
            log.getTotalTokens(),
            log.getUsageSource(),
            log.getEstimatedCost(),
            log.getCurrency(),
            log.getFailureCode(),
            log.getFailureMessage(),
            log.getCreatedAt()
        );
    }
}
