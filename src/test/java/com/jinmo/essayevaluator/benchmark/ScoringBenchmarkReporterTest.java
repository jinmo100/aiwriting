package com.jinmo.essayevaluator.benchmark;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringBenchmarkReporterTest {

    @Test
    void rendersSoftGateReportWithoutCallingRealProvider() {
        String markdown = ScoringBenchmarkReporter.renderMarkdown(ScoringBenchmarkDataset.loadDefault());

        assertThat(markdown).contains("# Scoring Consistency Benchmark Report");
        assertThat(markdown).contains("Soft gate");
        assertThat(markdown).contains("Total cases: 24");
        assertThat(markdown).contains("JUNIOR_ZHONGKAO: 6");
        assertThat(markdown).contains("SENIOR_GAOKAO: 6");
        assertThat(markdown).contains("不会调用真实 Provider");
        assertThat(markdown).contains("| ID | Type | Expected normalized | Focus |");
    }
}
