package com.jinmo.aiwriting.domain.dto;

import java.util.List;

/**
 * 评分结果响应DTO
 */
public record EssayScoreResponse(
    Long essayId,
    ScoreDetail score,
    Integer processingTime
) {
    public record ScoreDetail(
        Double overallScore,
        Double contentScore,
        Double languageScore,
        Double structureScore,
        Double coherenceScore,
        List<String> strengths,
        List<String> suggestions,
        List<ErrorDetail> errors,
        String detailedFeedback
    ) {}

    public record ErrorDetail(
        String sentence,
        String type,
        String description,
        String correction
    ) {}
}
