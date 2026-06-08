package com.jinmo.essayevaluator.benchmark;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class ScoringBenchmarkReporter {

    private ScoringBenchmarkReporter() {
    }

    static String renderMarkdown(List<ScoringBenchmarkCase> cases) {
        Map<String, Long> counts = cases.stream()
            .collect(Collectors.groupingBy(ScoringBenchmarkCase::essayType, TreeMap::new, Collectors.counting()));

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Scoring Consistency Benchmark Report\n\n");
        markdown.append("- Generated at: ").append(OffsetDateTime.now()).append("\n");
        markdown.append("- Mode: Soft gate（不会调用真实 Provider，不消耗用户 API Key）\n");
        markdown.append("- Total cases: ").append(cases.size()).append("\n\n");

        markdown.append("## Distribution\n\n");
        counts.forEach((type, count) -> markdown.append("- ").append(type).append(": ").append(count).append("\n"));

        markdown.append("\n## Soft gate rules\n\n");
        markdown.append("1. 基准集样例数量和类型分布必须稳定。\n");
        markdown.append("2. 每篇样例保留期望 100 分换算区间和关注标签，供后续真实 Provider 回放比对。\n");
        markdown.append("3. 当前报告只生成离线清单，不因为模型分数波动阻断 CI。\n");
        markdown.append("4. 后续接入真实回放时，超出期望区间只标记 `WARN`，由维护者复核 Prompt/Rubric/Provider 变更。\n\n");

        markdown.append("## Cases\n\n");
        markdown.append("| ID | Type | Expected normalized | Focus |\n");
        markdown.append("|---|---|---:|---|\n");
        for (ScoringBenchmarkCase testCase : cases) {
            markdown
                .append("| ")
                .append(testCase.id())
                .append(" | ")
                .append(testCase.essayType())
                .append(" | ")
                .append(testCase.expectedNormalizedMin())
                .append("-")
                .append(testCase.expectedNormalizedMax())
                .append(" | ")
                .append(String.join(", ", testCase.focusTags()))
                .append(" |\n");
        }
        return markdown.toString();
    }
}
