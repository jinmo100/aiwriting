package com.jinmo.essayevaluator.ai;

import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.dto.RubricDefinition;
import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;
import com.jinmo.essayevaluator.domain.entity.RubricDimension;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ScoringResultValidator {

    private static final Set<String> ALLOWED_CONFIDENCE_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");

    public RubricScoringResult validateAndNormalize(RubricScoringResult result, RubricDefinition rubric) {
        if (result == null) {
            throw new BusinessException("评分结果不能为空");
        }
        if (rubric == null || rubric.profile() == null || rubric.version() == null || rubric.dimensions().isEmpty()) {
            throw new BusinessException("评分标准不能为空");
        }
        if (result.dimensions().isEmpty()) {
            throw new BusinessException("评分结果缺少 dimensions");
        }

        Map<String, RubricDimension> expected = rubric.dimensions().stream()
            .collect(Collectors.toMap(
                RubricDimension::getDimensionKey,
                Function.identity(),
                (a, b) -> a,
                LinkedHashMap::new
            ));
        Map<String, RubricScoringResult.Dimension> actual = new LinkedHashMap<>();
        for (RubricScoringResult.Dimension dimension : result.dimensions()) {
            if (dimension.key() == null || dimension.key().isBlank()) {
                throw new BusinessException("评分维度缺少 key");
            }
            if (actual.put(dimension.key(), dimension) != null) {
                throw new BusinessException("评分结果存在重复维度: " + dimension.key());
            }
        }
        for (String key : actual.keySet()) {
            if (!expected.containsKey(key)) {
                throw new BusinessException("评分结果包含未配置维度: " + key);
            }
        }

        List<RubricScoringResult.Dimension> normalizedDimensions = expected.values().stream()
            .map(definition -> normalizeDimension(definition, actual.get(definition.getDimensionKey())))
            .toList();

        double nativeValue = calculateNativeScore(rubric, normalizedDimensions);
        double maxNative = rubric.version().getMaxNativeScore();
        int normalizedValue = (int) Math.round(nativeValue / maxNative * 100.0);
        normalizedValue = Math.max(0, Math.min(100, normalizedValue));

        String nativeScale = rubric.version().getNativeScale();
        RubricScoringResult.ScoreValue nativeScore = new RubricScoringResult.ScoreValue(
            nativeScale,
            nativeValue,
            maxNative,
            formatNativeDisplay(nativeScale, nativeValue, maxNative)
        );
        RubricScoringResult.ScoreValue normalizedScore = new RubricScoringResult.ScoreValue(
            "PERCENT_100",
            (double) normalizedValue,
            100.0,
            normalizedValue + "/100"
        );
        String gradeLabel = gradeLabel(nativeScale, nativeValue, normalizedValue);

        RubricScoringResult.Confidence confidence = normalizeConfidence(result.confidence());
        RubricScoringResult.Summary summary = normalizeSummary(result.summary());

        return new RubricScoringResult(
            nativeScore,
            normalizedScore,
            new RubricScoringResult.RubricInfo(
                rubric.profile().getTypeCode(),
                rubric.version().getVersion(),
                rubric.profile().getDisplayName()
            ),
            gradeLabel,
            confidence,
            normalizedDimensions,
            result.annotations(),
            summary,
            result.safetyNotice(),
            result.inputAnalysis()
        );
    }

    private RubricScoringResult.Dimension normalizeDimension(
        RubricDimension definition,
        RubricScoringResult.Dimension actual
    ) {
        if (actual == null) {
            throw new BusinessException("评分结果缺少维度: " + definition.getDimensionKey());
        }
        requireScore(definition.getDimensionKey(), actual.score(), 0, definition.getMaxScore());
        return new RubricScoringResult.Dimension(
            definition.getDimensionKey(),
            definition.getLabel(),
            roundToOneDecimal(actual.score()),
            definition.getMaxScore(),
            actual.level() == null || actual.level().isBlank()
                ? levelLabel(actual.score() / definition.getMaxScore())
                : actual.level(),
            defaultString(actual.reason()),
            actual.evidence(),
            defaultString(actual.improvement())
        );
    }

    private double calculateNativeScore(RubricDefinition rubric, List<RubricScoringResult.Dimension> dimensions) {
        String nativeScale = rubric.version().getNativeScale();
        if (nativeScale != null && nativeScale.startsWith("IELTS")) {
            return roundToNearestHalf(dimensions.stream().mapToDouble(RubricScoringResult.Dimension::score).average().orElse(0));
        }
        if (nativeScale != null && nativeScale.startsWith("TOEFL")) {
            return roundToNearestHalf(dimensions.stream().mapToDouble(RubricScoringResult.Dimension::score).average().orElse(0));
        }

        double weightedScore = 0;
        double weightTotal = 0;
        Map<String, RubricDimension> definitionByKey = rubric.dimensions().stream()
            .collect(Collectors.toMap(RubricDimension::getDimensionKey, Function.identity()));
        for (RubricScoringResult.Dimension dimension : dimensions) {
            RubricDimension definition = definitionByKey.get(dimension.key());
            double weight = definition.getWeight() == null ? definition.getMaxScore() : definition.getWeight();
            weightedScore += (dimension.score() / definition.getMaxScore()) * weight;
            weightTotal += weight;
        }
        double value = weightTotal == 0 ? 0 : (weightedScore / weightTotal) * rubric.version().getMaxNativeScore();
        if ("PERCENT_100".equals(nativeScale)) {
            return (double) Math.round(value);
        }
        return roundToNearestHalf(value);
    }

    private static void requireScore(String field, Double value, double min, double max) {
        if (value == null) {
            throw new BusinessException("评分结果缺少维度分数: " + field);
        }
        if (value < min || value > max) {
            throw new BusinessException(field + " 超出范围: " + value + "，允许范围 0-" + max);
        }
    }

    private RubricScoringResult.Confidence normalizeConfidence(RubricScoringResult.Confidence confidence) {
        if (confidence == null) {
            return new RubricScoringResult.Confidence("MEDIUM", 0.70, List.of("AI 评分存在主观性"), List.of());
        }
        double score = confidence.score() == null ? 0.70 : Math.max(0, Math.min(1, confidence.score()));
        String level = confidence.level();
        if (level == null || !ALLOWED_CONFIDENCE_LEVELS.contains(level)) {
            level = score >= 0.80 ? "HIGH" : score >= 0.55 ? "MEDIUM" : "LOW";
        }
        return new RubricScoringResult.Confidence(level, score, confidence.reasons(), confidence.warnings());
    }

    private RubricScoringResult.Summary normalizeSummary(RubricScoringResult.Summary summary) {
        if (summary == null) {
            return new RubricScoringResult.Summary(List.of(), List.of(), "");
        }
        return new RubricScoringResult.Summary(
            summary.strengths(),
            summary.priorityImprovements(),
            defaultString(summary.overallFeedback())
        );
    }

    private static String gradeLabel(String nativeScale, double nativeValue, int normalizedValue) {
        if (nativeScale != null && nativeScale.startsWith("IELTS")) {
            if (nativeValue >= 8.0) return "高分段";
            if (nativeValue >= 7.0) return "良好";
            if (nativeValue >= 6.0) return "合格";
            if (nativeValue >= 5.0) return "基础";
            return "需提升";
        }
        if (nativeScale != null && nativeScale.startsWith("TOEFL")) {
            if (nativeValue >= 4.5) return "高分段";
            if (nativeValue >= 4.0) return "良好";
            if (nativeValue >= 3.0) return "合格";
            if (nativeValue >= 2.0) return "基础";
            return "需提升";
        }
        if (normalizedValue >= 90) return "优秀";
        if (normalizedValue >= 80) return "良好";
        if (normalizedValue >= 70) return "中等";
        if (normalizedValue >= 60) return "及格";
        return "需改进";
    }

    private static String formatNativeDisplay(String nativeScale, double value, double max) {
        if (nativeScale != null && nativeScale.startsWith("IELTS")) {
            return "Band " + String.format(java.util.Locale.ROOT, "%.1f", value);
        }
        return formatNumber(value) + "/" + formatNumber(max);
    }

    private static String levelLabel(double ratio) {
        if (ratio >= 0.90) return "优秀";
        if (ratio >= 0.80) return "良好";
        if (ratio >= 0.70) return "中等";
        if (ratio >= 0.60) return "及格";
        return "需改进";
    }

    private static double roundToNearestHalf(double value) {
        return Math.round(value * 2.0) / 2.0;
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
