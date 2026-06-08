package com.jinmo.essayevaluator.domain.dto;

import java.time.LocalDateTime;

public record EssayScoreResponse(
    Long essayId,
    Long scoreId,
    EssayResponse essay,
    RubricScoringResult result,
    String scoringStatus,
    String aiModel,
    Integer tokensUsed,
    Integer processingTime,
    AiUsageSummary aiUsage,
    String rubricType,
    String rubricVersion,
    Double nativeScore,
    String nativeScoreDisplay,
    Double normalizedScore,
    String gradeLabel,
    String confidenceLevel,
    String idempotencyKey,
    String contentHash,
    String errorCode,
    String errorMessage,
    Integer attemptCount,
    Boolean retryable,
    LocalDateTime createdAt
) {
}
