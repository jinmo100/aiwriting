package com.jinmo.aiwriting.domain.dto;

import java.util.List;

/**
 * AI评分结果DTO
 */
public record ScoringResult(
    Double overallScore,
    Double contentScore,
    Double languageScore,
    Double structureScore,
    Double coherenceScore,
    List<String> strengths,
    List<String> suggestions,
    List<ErrorDetail> errors,
    String detailedFeedback
) {
    public record ErrorDetail(
        String sentence,
        String type,
        String description,
        String correction
    ) {}
}
