package com.jinmo.aiwriting.ai;

import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.RubricScoringResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringResultValidatorTest {

    private final ScoringResultValidator validator = new ScoringResultValidator();

    @Test
    void acceptsValidRubricResultAndComputesNativeScores() {
        RubricScoringResult normalized = validator.validateAndNormalize(generalResult(27, 22, 23, 16), RubricTestFixtures.generalRubric());

        assertThat(normalized.nativeScore().value()).isEqualTo(88.0);
        assertThat(normalized.normalizedScore().value()).isEqualTo(88.0);
        assertThat(normalized.gradeLabel()).isEqualTo("良好");
        assertThat(normalized.dimensions()).extracting(RubricScoringResult.Dimension::label)
            .containsExactly("内容质量", "结构组织", "语言准确性", "表达丰富度");
    }

    @Test
    void roundsIeltsNativeScoreToNearestHalfBand() {
        RubricScoringResult result = new RubricScoringResult(
            null,
            null,
            null,
            null,
            new RubricScoringResult.Confidence("HIGH", 0.9, List.of(), List.of()),
            List.of(
                dimension("task_response", 7.0),
                dimension("coherence_cohesion", 7.0),
                dimension("lexical_resource", 6.5),
                dimension("grammar_range_accuracy", 6.5)
            ),
            List.of(),
            new RubricScoringResult.Summary(List.of("a", "b"), List.of("c", "d"), "feedback"),
            "",
            null
        );

        RubricScoringResult normalized = validator.validateAndNormalize(result, RubricTestFixtures.ieltsRubric());

        assertThat(normalized.nativeScore().value()).isEqualTo(7.0);
        assertThat(normalized.nativeScore().display()).isEqualTo("Band 7.0");
        assertThat(normalized.normalizedScore().value()).isEqualTo(78.0);
        assertThat(normalized.gradeLabel()).isEqualTo("良好");
    }

    @Test
    void rejectsOutOfRangeDimensionScores() {
        assertThatThrownBy(() -> validator.validateAndNormalize(generalResult(31, 22, 23, 16), RubricTestFixtures.generalRubric()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("content_quality");
    }

    @Test
    void rejectsMissingDimensions() {
        RubricScoringResult result = new RubricScoringResult(
            null,
            null,
            null,
            null,
            null,
            List.of(dimension("content_quality", 20.0)),
            List.of(),
            new RubricScoringResult.Summary(List.of(), List.of(), ""),
            "",
            null
        );

        assertThatThrownBy(() -> validator.validateAndNormalize(result, RubricTestFixtures.generalRubric()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("缺少维度");
    }

    @Test
    void acceptsConfidenceDefaults() {
        assertThatCode(() -> validator.validateAndNormalize(generalResult(27, 22, 23, 16), RubricTestFixtures.generalRubric()))
            .doesNotThrowAnyException();
    }

    private static RubricScoringResult generalResult(double content, double organization, double language, double expression) {
        return new RubricScoringResult(
            null,
            null,
            null,
            null,
            new RubricScoringResult.Confidence("HIGH", 0.86, List.of("clear"), List.of()),
            List.of(
                dimension("content_quality", content),
                dimension("organization", organization),
                dimension("language_accuracy", language),
                dimension("expression", expression)
            ),
            List.of(),
            new RubricScoringResult.Summary(List.of("clear argument"), List.of("add examples"), "Good work."),
            "",
            null
        );
    }

    private static RubricScoringResult.Dimension dimension(String key, double score) {
        return new RubricScoringResult.Dimension(key, null, score, null, null, "reason", List.of("evidence"), "improvement");
    }
}
