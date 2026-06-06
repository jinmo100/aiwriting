package com.jinmo.aiwriting.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderModelListParserTest {

    private final ProviderModelListParser parser = new ProviderModelListParser(new ObjectMapper());

    @Test
    void parsesOpenAiCompatibleModels() {
        String json = """
            {"data":[{"id":"gpt-4o-mini","owned_by":"openai"},{"id":"custom-model"}]}
            """;

        List<ProviderModelInfo> models = parser.parse(ProviderType.OPENAI_CHAT_COMPLETIONS, json);

        assertThat(models).extracting(ProviderModelInfo::id).containsExactly("gpt-4o-mini", "custom-model");
        assertThat(models.getFirst().ownedBy()).isEqualTo("openai");
    }

    @Test
    void parsesGeminiModelsAndStripsModelsPrefix() {
        String json = """
            {"models":[{"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash"}]}
            """;

        List<ProviderModelInfo> models = parser.parse(ProviderType.GEMINI_GENERATE_CONTENT, json);

        assertThat(models).containsExactly(new ProviderModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", null));
    }
}
