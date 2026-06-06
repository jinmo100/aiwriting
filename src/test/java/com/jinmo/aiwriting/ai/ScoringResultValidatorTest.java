package com.jinmo.aiwriting.ai;

import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.ScoringResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringResultValidatorTest {

    private final ScoringResultValidator validator = new ScoringResultValidator();

    @Test
    void acceptsValidScoringResult() {
        ScoringResult result = new ScoringResult(
            88.0, 27.0, 27.0, 18.0, 16.0,
            List.of("clear argument"),
            List.of("add examples"),
            List.of(new ScoringResult.ErrorDetail("A sentence", "GRAMMAR", "Issue", "Fix")),
            "Good work."
        );

        assertThatCode(() -> validator.validate(result)).doesNotThrowAnyException();
    }

    @Test
    void rejectsOutOfRangeScores() {
        ScoringResult result = new ScoringResult(
            101.0, 27.0, 27.0, 18.0, 16.0,
            List.of("clear argument"), List.of("add examples"), List.of(), "Good work."
        );

        assertThatThrownBy(() -> validator.validate(result))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("overallScore");
    }

    @Test
    void rejectsUnsupportedErrorType() {
        ScoringResult result = new ScoringResult(
            88.0, 27.0, 27.0, 18.0, 16.0,
            List.of("clear argument"), List.of("add examples"),
            List.of(new ScoringResult.ErrorDetail("A sentence", "UNKNOWN", "Issue", "Fix")),
            "Good work."
        );

        assertThatThrownBy(() -> validator.validate(result))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("错误类型");
    }
}
