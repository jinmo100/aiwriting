package com.jinmo.essayevaluator.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScoringBenchmarkReportMain {
    public static void main(String[] args) throws IOException {
        Path output = args.length > 0
            ? Path.of(args[0])
            : Path.of("build", "reports", "scoring-benchmark", "report.md");
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, ScoringBenchmarkReporter.renderMarkdown(ScoringBenchmarkDataset.loadDefault()));
        System.out.println("Scoring benchmark report generated: " + output.toAbsolutePath());
    }
}
