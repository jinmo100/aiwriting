package com.jinmo.aiwriting.service.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyAnalyzerTest {

    private final SafetyAnalyzer analyzer = new SafetyAnalyzer();

    @Test
    void rejectsChineseIdNumber() {
        SafetyAnalysis analysis = analyzer.analyze("题目", "My ID is 11010519900307001X and this should not be submitted.");

        assertThat(analysis.status()).isEqualTo("REJECT");
        assertThat(analysis.issues()).anyMatch(issue -> "PERSONAL_DATA".equals(issue.category()));
    }

    @Test
    void rejectsAcademicIntegrityBypassRequests() {
        SafetyAnalysis analysis = analyzer.analyze("帮我写一篇可以直接交的作文", "This is not a real essay.");

        assertThat(analysis.status()).isEqualTo("REJECT");
        assertThat(analysis.issues()).anyMatch(issue -> "ACADEMIC_INTEGRITY".equals(issue.category()));
    }

    @Test
    void warnsPhoneNumber() {
        SafetyAnalysis analysis = analyzer.analyze("题目", "My phone number is 13800138000 but the rest is an essay.");

        assertThat(analysis.status()).isEqualTo("WARN");
        assertThat(analysis.confidenceWarnings()).anyMatch(item -> item.contains("手机号"));
    }
}
