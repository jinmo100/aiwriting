package com.jinmo.essayevaluator.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderAdvancedParameterMapperTest {

    private final ProviderAdvancedParameterMapper mapper = new ProviderAdvancedParameterMapper(new ObjectMapper());

    @Test
    void keepsOnlyOpenAiWhitelistedParameters() {
        Map<String, Object> mapped = mapper.map(
            ProviderType.OPENAI_CHAT_COMPLETIONS,
            "{\"top_p\":0.9,\"presence_penalty\":0.2,\"unknown_param\":\"ignored\"}"
        );

        assertThat(mapped).containsEntry("top_p", 0.9);
        assertThat(mapped).containsEntry("presence_penalty", 0.2);
        assertThat(mapped).doesNotContainKey("unknown_param");
    }

    @Test
    void keepsOnlyGeminiWhitelistedParameters() {
        Map<String, Object> mapped = mapper.map(
            ProviderType.GEMINI_GENERATE_CONTENT,
            "{\"top_p\":0.8,\"top_k\":40,\"reasoning_effort\":\"medium\"}"
        );

        assertThat(mapped).containsEntry("top_p", 0.8);
        assertThat(mapped).containsEntry("top_k", 40);
        assertThat(mapped).doesNotContainKey("reasoning_effort");
    }

    @Test
    void invalidJsonMapsToEmptyParameters() {
        assertThat(mapper.map(ProviderType.OPENAI_CHAT_COMPLETIONS, "not-json")).isEmpty();
    }
}
