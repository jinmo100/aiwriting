package com.jinmo.aiwriting.domain.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

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
    ) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public ErrorDetail(String description) {
            this("", "STYLE", description, "");
        }
    }
}
