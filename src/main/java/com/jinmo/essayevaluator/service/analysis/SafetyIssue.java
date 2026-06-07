package com.jinmo.essayevaluator.service.analysis;

public record SafetyIssue(
    String category,
    String severity,
    String action,
    String message
) {
}
