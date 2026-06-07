package com.jinmo.aiwriting.service.analysis;

public record SafetyIssue(
    String category,
    String severity,
    String action,
    String message
) {
}
