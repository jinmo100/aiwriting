package com.jinmo.essayevaluator.domain.dto;

import java.time.LocalDateTime;

public record EssayHistoryItem(
    Long essayId,
    String essayType,
    String essayTypeDisplayName,
    String taskPromptSummary,
    String contentSummary,
    Integer wordCount,
    Long essayGroupId,
    Integer versionNo,
    Long parentEssayId,
    String nativeScoreDisplay,
    Double normalizedScore,
    String gradeLabel,
    String confidenceLevel,
    String scoringStatus,
    String aiModel,
    LocalDateTime createdAt
) {
}
