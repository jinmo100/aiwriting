package com.jinmo.essayevaluator.service.analysis;

import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;

public record InputInspection(
    RubricScoringResult.InputAnalysis inputAnalysis,
    SafetyAnalysis safetyAnalysis
) {
}
