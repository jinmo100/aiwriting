package com.jinmo.aiwriting.ai;

import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.ScoringResult;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ScoringResultValidator {

    private static final Set<String> ALLOWED_ERROR_TYPES = Set.of(
        "GRAMMAR",
        "SPELLING",
        "PUNCTUATION",
        "VOCABULARY",
        "COLLOCATION",
        "STYLE",
        "CLARITY",
        "COHERENCE",
        "TASK_RESPONSE",
        "STRUCTURE"
    );

    public void validate(ScoringResult result) {
        if (result == null) {
            throw new BusinessException("评分结果不能为空");
        }
        requireScore("overallScore", result.overallScore(), 0, 100);
        requireScore("contentScore", result.contentScore(), 0, 30);
        requireScore("languageScore", result.languageScore(), 0, 30);
        requireScore("structureScore", result.structureScore(), 0, 20);
        requireScore("coherenceScore", result.coherenceScore(), 0, 20);
        if (result.strengths() == null) {
            throw new BusinessException("评分结果缺少 strengths");
        }
        if (result.suggestions() == null) {
            throw new BusinessException("评分结果缺少 suggestions");
        }
        if (result.errors() == null) {
            throw new BusinessException("评分结果缺少 errors");
        }
        if (result.detailedFeedback() == null || result.detailedFeedback().isBlank()) {
            throw new BusinessException("评分结果缺少 detailedFeedback");
        }
        for (ScoringResult.ErrorDetail error : result.errors()) {
            if (error.type() == null || !ALLOWED_ERROR_TYPES.contains(error.type())) {
                throw new BusinessException("不支持的错误类型: " + error.type());
            }
        }
    }

    private static void requireScore(String field, Double value, int min, int max) {
        if (value == null) {
            throw new BusinessException("评分结果缺少 " + field);
        }
        if (value < min || value > max) {
            throw new BusinessException(field + " 超出范围: " + value);
        }
    }
}
