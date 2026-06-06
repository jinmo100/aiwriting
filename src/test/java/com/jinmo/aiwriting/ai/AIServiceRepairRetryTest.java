package com.jinmo.aiwriting.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.ai.provider.AIProviderAdapter;
import com.jinmo.aiwriting.ai.provider.AIProviderRequest;
import com.jinmo.aiwriting.ai.provider.AIProviderResult;
import com.jinmo.aiwriting.ai.provider.ProviderAdapterRegistry;
import com.jinmo.aiwriting.ai.provider.ProviderType;
import com.jinmo.aiwriting.domain.dto.ScoringResult;
import com.jinmo.aiwriting.domain.entity.ApiConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AIServiceRepairRetryTest {

    @Test
    void retriesOnceWithRepairPromptWhenFirstStructuredOutputIsInvalid() {
        FakeAdapter adapter = new FakeAdapter();
        AIService service = new AIService(
            new ProviderAdapterRegistry(List.of(adapter)),
            new ObjectMapper(),
            new ScoringResultValidator()
        );
        ApiConfig config = new ApiConfig();
        config.setProviderType(ProviderType.OPENAI_CHAT_COMPLETIONS);
        config.setModelName("test-model");

        ScoringResult result = service.scoreEssay("This is an English essay with enough words to trigger scoring behavior in tests.", config);

        assertThat(result.overallScore()).isEqualTo(88.0);
        assertThat(adapter.calls).isEqualTo(2);
        assertThat(adapter.lastPrompt).contains("修复为合法 JSON");
    }

    @Test
    void acceptsStringItemsInErrorsArrayWithoutRepair() {
        FakeAdapter adapter = new FakeAdapter("""
            {"overallScore":88,"contentScore":27,"languageScore":27,"structureScore":18,"coherenceScore":16,"strengths":["clear argument"],"suggestions":["add examples"],"errors":["句子中标点和空格需要调整。"],"detailedFeedback":"Good work."}
            """);
        AIService service = new AIService(
            new ProviderAdapterRegistry(List.of(adapter)),
            new ObjectMapper(),
            new ScoringResultValidator()
        );
        ApiConfig config = new ApiConfig();
        config.setProviderType(ProviderType.OPENAI_CHAT_COMPLETIONS);
        config.setModelName("test-model");

        ScoringResult result = service.scoreEssay("This is an English essay with enough words to trigger scoring behavior in tests.", config);

        assertThat(adapter.calls).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).type()).isEqualTo("STYLE");
        assertThat(result.errors().get(0).description()).isEqualTo("句子中标点和空格需要调整。");
        assertThat(result.errors().get(0).sentence()).isEmpty();
        assertThat(result.errors().get(0).correction()).isEmpty();
    }

    private static class FakeAdapter implements AIProviderAdapter {
        int calls;
        String lastPrompt;
        private final String fixedResponse;

        FakeAdapter() {
            this.fixedResponse = null;
        }

        FakeAdapter(String fixedResponse) {
            this.fixedResponse = fixedResponse;
        }

        @Override
        public ProviderType providerType() {
            return ProviderType.OPENAI_CHAT_COMPLETIONS;
        }

        @Override
        public AIProviderResult generate(AIProviderRequest request, ApiConfig config) {
            calls++;
            lastPrompt = request.userPrompt();
            if (fixedResponse != null) {
                return new AIProviderResult(fixedResponse, null, null, config.getModelName(), null, null, null, 1L, providerType());
            }
            if (calls == 1) {
                return new AIProviderResult("not json", null, null, config.getModelName(), null, null, null, 1L, providerType());
            }
            return new AIProviderResult("""
                {"overallScore":88,"contentScore":27,"languageScore":27,"structureScore":18,"coherenceScore":16,"strengths":["clear argument"],"suggestions":["add examples"],"errors":[],"detailedFeedback":"Good work."}
                """, null, null, config.getModelName(), null, null, null, 1L, providerType());
        }
    }
}
