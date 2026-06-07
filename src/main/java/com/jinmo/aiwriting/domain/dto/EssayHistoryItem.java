package com.jinmo.aiwriting.domain.dto;

import java.time.LocalDateTime;

public record EssayHistoryItem(
    Long essayId,
    String essayType,
    String essayTypeDisplayName,
    String taskPromptSummary,
    String contentSummary,
    Integer wordCount,
    String nativeScoreDisplay,
    Double normalizedScore,
    String gradeLabel,
    String confidenceLevel,
    String scoringStatus,
    String aiModel,
    LocalDateTime createdAt
) {
}
