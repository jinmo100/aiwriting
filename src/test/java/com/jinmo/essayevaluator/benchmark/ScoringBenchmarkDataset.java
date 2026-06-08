package com.jinmo.essayevaluator.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

final class ScoringBenchmarkDataset {
    private static final String DEFAULT_RESOURCE = "/benchmark/scoring-baseline-v1.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ScoringBenchmarkDataset() {
    }

    static List<ScoringBenchmarkCase> loadDefault() {
        try (InputStream input = ScoringBenchmarkDataset.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Benchmark resource not found: " + DEFAULT_RESOURCE);
            }
            BenchmarkFile file = OBJECT_MAPPER.readValue(input, BenchmarkFile.class);
            return file.cases() == null ? List.of() : List.copyOf(file.cases());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load scoring benchmark dataset", e);
        }
    }

    private record BenchmarkFile(String version, List<ScoringBenchmarkCase> cases) {
    }
}
