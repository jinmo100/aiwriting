package com.jinmo.aiwriting.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 动态 Rubric 评分结果。该结构面向所有作文类型，不包含旧四维固定字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RubricScoringResult(
    ScoreValue nativeScore,
    ScoreValue normalizedScore,
    RubricInfo rubric,
    String gradeLabel,
    Confidence confidence,
    List<Dimension> dimensions,
    List<Annotation> annotations,
    Summary summary,
    String safetyNotice,
    InputAnalysis inputAnalysis
) {
    public RubricScoringResult {
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        annotations = annotations == null ? List.of() : List.copyOf(annotations);
        if (summary == null) {
            summary = new Summary(List.of(), List.of(), "");
        }
        if (confidence == null) {
            confidence = new Confidence("MEDIUM", 0.70, List.of(), List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreValue(
        String scale,
        Double value,
        Double max,
        String display
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RubricInfo(
        String type,
        String version,
        String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Confidence(
        String level,
        Double score,
        List<String> reasons,
        List<String> warnings
    ) {
        public Confidence {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dimension(
        String key,
        String label,
        Double score,
        Double maxScore,
        String level,
        String reason,
        List<String> evidence,
        String improvement
    ) {
        public Dimension {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Annotation(
        String type,
        String severity,
        String original,
        String context,
        String message,
        String suggestion,
        String explanation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
        List<String> strengths,
        List<String> priorityImprovements,
        String overallFeedback
    ) {
        public Summary {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            priorityImprovements = priorityImprovements == null ? List.of() : List.copyOf(priorityImprovements);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InputAnalysis(
        String status,
        Integer wordCount,
        Integer charCount,
        List<String> warnings,
        List<String> rejections
    ) {
        public InputAnalysis {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            rejections = rejections == null ? List.of() : List.copyOf(rejections);
        }
    }
}
