package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.domain.dto.RubricScoringResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagQueryBuilderTest {

    @Test
    void includesAtMostThreeLowScoreDimensions() {
        String query = new RagQueryBuilder().build(resultWithDimensions(), "GENERAL", null);

        assertThat(query)
            .contains("内容")
            .contains("语言")
            .contains("结构")
            .doesNotContain("连贯");
    }

    @Test
    void includesAtMostFiveAnnotations() {
        String query = new RagQueryBuilder().build(resultWithAnnotations(6), "GENERAL", null);

        assertThat(query).contains("批注 1").contains("批注 5");
        assertThat(query).doesNotContain("批注 6");
    }

    @Test
    void includesAtMostThreePriorityImprovements() {
        String query = new RagQueryBuilder().build(resultWithImprovements(4), "GENERAL", null);

        assertThat(query).contains("改进 1").contains("改进 3");
        assertThat(query).doesNotContain("改进 4");
    }

    @Test
    void includesTaskPromptSummaryForSpecificEssayType() {
        String query = new RagQueryBuilder().build(
            resultWithImprovements(1),
            "SENIOR_GAOKAO",
            "请你给校英语报写一封信，介绍一次志愿服务经历，并说明你的收获。"
        );

        assertThat(query)
            .contains("任务要求")
            .contains("志愿服务经历");
    }

    private RubricScoringResult resultWithDimensions() {
        return new RubricScoringResult(
            null,
            null,
            null,
            null,
            null,
            List.of(
                dimension("content", "内容", 5.0, 10.0),
                dimension("language", "语言", 6.0, 10.0),
                dimension("structure", "结构", 7.0, 10.0),
                dimension("coherence", "连贯", 7.4, 10.0)
            ),
            List.of(),
            null,
            null,
            null,
            null
        );
    }

    private RubricScoringResult resultWithAnnotations(int count) {
        return new RubricScoringResult(
            null,
            null,
            null,
            null,
            null,
            List.of(),
            java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new RubricScoringResult.Annotation(
                    "GRAMMAR",
                    "MEDIUM",
                    "quote",
                    null,
                    null,
                    "批注 " + i,
                    "建议 " + i,
                    null
                ))
                .toList(),
            null,
            null,
            null,
            null
        );
    }

    private RubricScoringResult resultWithImprovements(int count) {
        return new RubricScoringResult(
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            new RubricScoringResult.Summary(
                List.of(),
                java.util.stream.IntStream.rangeClosed(1, count).mapToObj(i -> "改进 " + i).toList(),
                ""
            ),
            null,
            null,
            null
        );
    }

    private RubricScoringResult.Dimension dimension(String key, String label, Double score, Double maxScore) {
        return new RubricScoringResult.Dimension(
            key,
            label,
            score,
            maxScore,
            "LOW",
            label + " 原因",
            List.of(),
            label + " 改进"
        );
    }
}
