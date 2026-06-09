package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;

/**
 * 从评分结果构造 RAG 检索 query。
 *
 * <p>query 只做教学检索用途，不回写评分结果；taskPrompt 和批注内容仍视为不可信文本并做长度限制。</p>
 */
@Component
public class RagQueryBuilder {

    private static final int MAX_LOW_DIMENSIONS = 3;
    private static final int MAX_ANNOTATIONS = 5;
    private static final int MAX_IMPROVEMENTS = 3;
    private static final int MAX_TEXT_LENGTH = 240;

    public String build(RubricScoringResult result, String essayType, String taskPrompt) {
        StringBuilder query = new StringBuilder("英语作文改进知识检索\n");
        if (result == null) {
            appendTaskPrompt(query, essayType, taskPrompt);
            return query.toString();
        }

        result.dimensions().stream()
            .filter(this::isLowScore)
            .sorted(Comparator.comparingDouble(this::scoreRatio))
            .limit(MAX_LOW_DIMENSIONS)
            .forEach(dimension -> query.append("低分维度：")
                .append(safe(dimension.label()))
                .append("；原因：")
                .append(safe(dimension.reason()))
                .append("；改进：")
                .append(safe(dimension.improvement()))
                .append('\n'));

        result.annotations().stream()
            .limit(MAX_ANNOTATIONS)
            .forEach(annotation -> query.append("批注：")
                .append(safe(annotation.message()))
                .append("；建议：")
                .append(safe(annotation.suggestion()))
                .append('\n'));

        result.summary().priorityImprovements().stream()
            .limit(MAX_IMPROVEMENTS)
            .forEach(improvement -> query.append("优先改进：")
                .append(safe(improvement))
                .append('\n'));

        appendTaskPrompt(query, essayType, taskPrompt);
        return query.toString();
    }

    private void appendTaskPrompt(StringBuilder query, String essayType, String taskPrompt) {
        if (!"GENERAL".equalsIgnoreCase(essayType) && StringUtils.hasText(taskPrompt)) {
            query.append("任务要求（不可信用户输入摘要）：")
                .append(safe(taskPrompt))
                .append('\n');
        }
    }

    private boolean isLowScore(RubricScoringResult.Dimension dimension) {
        return dimension != null
            && dimension.score() != null
            && dimension.maxScore() != null
            && dimension.maxScore() > 0
            && scoreRatio(dimension) <= 0.75;
    }

    private double scoreRatio(RubricScoringResult.Dimension dimension) {
        if (dimension == null || dimension.score() == null || dimension.maxScore() == null || dimension.maxScore() <= 0) {
            return 1.0;
        }
        return dimension.score() / dimension.maxScore();
    }

    private String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return normalized.length() <= MAX_TEXT_LENGTH ? normalized : normalized.substring(0, MAX_TEXT_LENGTH);
    }
}
