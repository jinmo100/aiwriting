package com.jinmo.aiwriting.service.analysis;

import com.jinmo.aiwriting.domain.dto.RubricScoringResult;

public record InputInspection(
    RubricScoringResult.InputAnalysis inputAnalysis,
    SafetyAnalysis safetyAnalysis
) {
}
