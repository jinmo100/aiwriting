package com.jinmo.essayevaluator.benchmark;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringBenchmarkDatasetTest {

    @Test
    void loadsTwentyFourCasesWithExamWeightedDistribution() {
        var cases = ScoringBenchmarkDataset.loadDefault();

        assertThat(cases).hasSize(24);
        assertThat(cases)
            .extracting(ScoringBenchmarkCase::id)
            .doesNotHaveDuplicates();
        assertThat(countByType(cases)).containsExactlyInAnyOrderEntriesOf(Map.of(
            "JUNIOR_ZHONGKAO", 6L,
            "SENIOR_GAOKAO", 6L,
            "CET4", 3L,
            "CET6", 3L,
            "IELTS_TASK_2", 3L,
            "GENERAL", 3L
        ));
    }

    @Test
    void everyBenchmarkCaseHasValidPromptContentAndExpectedRange() {
        var cases = ScoringBenchmarkDataset.loadDefault();

        assertThat(cases).allSatisfy(testCase -> {
            assertThat(testCase.id()).isNotBlank();
            assertThat(testCase.title()).isNotBlank();
            assertThat(testCase.content()).isNotBlank();
            assertThat(countEnglishWords(testCase.content())).isGreaterThanOrEqualTo(45);
            if (!"GENERAL".equals(testCase.essayType())) {
                assertThat(testCase.taskPrompt()).isNotBlank();
            }
            assertThat(testCase.expectedNormalizedMin()).isBetween(0, 100);
            assertThat(testCase.expectedNormalizedMax()).isBetween(0, 100);
            assertThat(testCase.expectedNormalizedMin()).isLessThanOrEqualTo(testCase.expectedNormalizedMax());
            assertThat(testCase.expectedGradeBand()).isNotBlank();
            assertThat(testCase.focusTags()).isNotEmpty();
        });
    }

    private static Map<String, Long> countByType(java.util.List<ScoringBenchmarkCase> cases) {
        return cases.stream()
            .collect(Collectors.groupingBy(ScoringBenchmarkCase::essayType, Collectors.counting()));
    }

    private static int countEnglishWords(String content) {
        var matcher = java.util.regex.Pattern.compile("[A-Za-z]+(?:[-'][A-Za-z]+)?").matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
