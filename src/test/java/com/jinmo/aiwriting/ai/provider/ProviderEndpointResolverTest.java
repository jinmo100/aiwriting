package com.jinmo.aiwriting.ai.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderEndpointResolverTest {

    private final ProviderEndpointResolver resolver = new ProviderEndpointResolver();

    @Test
    void normalizesProviderEndpointToBaseUrl() {
        assertThat(resolver.normalizeBaseUrl(ProviderType.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1/chat/completions/"))
            .isEqualTo("https://api.example.com/v1");
        assertThat(resolver.normalizeBaseUrl(ProviderType.OPENAI_RESPONSES, "https://api.example.com/v1/responses"))
            .isEqualTo("https://api.example.com/v1");
        assertThat(resolver.normalizeBaseUrl(ProviderType.ANTHROPIC_MESSAGES, "https://api.anthropic.com/v1/messages"))
            .isEqualTo("https://api.anthropic.com/v1");
        assertThat(resolver.normalizeBaseUrl(ProviderType.GEMINI_GENERATE_CONTENT, "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
            .isEqualTo("https://generativelanguage.googleapis.com/v1beta");
    }

    @Test
    void resolvesProviderSpecificEndpointFromBaseUrl() {
        assertThat(resolver.resolveGenerateEndpoint(ProviderType.OPENAI_CHAT_COMPLETIONS, "https://api.example.com/v1"))
            .isEqualTo("https://api.example.com/v1/chat/completions");
        assertThat(resolver.resolveGenerateEndpoint(ProviderType.OPENAI_RESPONSES, "https://api.example.com/v1/"))
            .isEqualTo("https://api.example.com/v1/responses");
        assertThat(resolver.resolveGenerateEndpoint(ProviderType.ANTHROPIC_MESSAGES, "https://api.anthropic.com/v1"))
            .isEqualTo("https://api.anthropic.com/v1/messages");
        assertThat(resolver.resolveGenerateEndpoint(ProviderType.GEMINI_GENERATE_CONTENT, "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"))
            .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
    }
}
