package com.jinmo.essayevaluator.benchmark;

import java.util.List;

record ScoringBenchmarkCase(
    String id,
    String essayType,
    String title,
    String taskPrompt,
    String content,
    int expectedNormalizedMin,
    int expectedNormalizedMax,
    String expectedGradeBand,
    List<String> focusTags
) {
    ScoringBenchmarkCase {
        focusTags = focusTags == null ? List.of() : List.copyOf(focusTags);
    }
}
