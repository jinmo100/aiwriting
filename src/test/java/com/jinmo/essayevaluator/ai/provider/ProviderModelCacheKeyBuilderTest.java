package com.jinmo.essayevaluator.ai.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderModelCacheKeyBuilderTest {

    private final ProviderModelCacheKeyBuilder builder = new ProviderModelCacheKeyBuilder();

    @Test
    void buildsRedisKeyWithoutLeakingBaseUrlOrApiKey() {
        String key = builder.build(ProviderType.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1/", "sk-secret-value");

        assertThat(key).startsWith("essay-evaluator:provider-models:OPENAI_CHAT_COMPLETIONS:");
        assertThat(key).doesNotContain("api.example.com");
        assertThat(key).doesNotContain("sk-secret-value");
    }

    @Test
    void normalizesTrailingSlashForStableKey() {
        String one = builder.build(ProviderType.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1", "sk-secret-value");
        String two = builder.build(ProviderType.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1/", "sk-secret-value");

        assertThat(one).isEqualTo(two);
    }
}
