package com.jinmo.essayevaluator.service.analysis;

import java.util.List;

public record SafetyAnalysis(
    String status,
    List<SafetyIssue> issues,
    String notice,
    List<String> confidenceWarnings
) {
    public SafetyAnalysis {
        issues = issues == null ? List.of() : List.copyOf(issues);
        confidenceWarnings = confidenceWarnings == null ? List.of() : List.copyOf(confidenceWarnings);
        notice = notice == null ? "" : notice;
    }

    public boolean rejected() {
        return AnalysisStatus.REJECT.name().equals(status);
    }

    public boolean warned() {
        return AnalysisStatus.WARN.name().equals(status);
    }
}
