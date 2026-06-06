package com.jinmo.aiwriting.ai.provider;

import com.jinmo.aiwriting.domain.entity.ApiConfig;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingAIProviderAdapterTest {

    @Test
    void retriesTransientProviderErrorsTwice() {
        FakeAdapter adapter = new FakeAdapter(AIProviderErrorCode.NETWORK_TIMEOUT, 2);
        ApiConfig config = new ApiConfig();
        config.setModelName("test-model");

        AIProviderResult result = adapter.generate(new AIProviderRequest(null, "hello", null, null, null), config);

        assertThat(result.text()).isEqualTo("ok");
        assertThat(adapter.attempts).isEqualTo(3);
    }

    @Test
    void doesNotRetryConfigurationErrors() {
        FakeAdapter adapter = new FakeAdapter(AIProviderErrorCode.AUTH_ERROR, Integer.MAX_VALUE);
        ApiConfig config = new ApiConfig();
        config.setModelName("test-model");

        assertThatThrownBy(() -> adapter.generate(new AIProviderRequest(null, "hello", null, null, null), config))
            .isInstanceOf(AIProviderException.class)
            .extracting("errorCode")
            .isEqualTo(AIProviderErrorCode.AUTH_ERROR);
        assertThat(adapter.attempts).isEqualTo(1);
    }

    private static class FakeAdapter extends RetryingAIProviderAdapter {
        private final AIProviderErrorCode errorCode;
        private final int failuresBeforeSuccess;
        int attempts;

        FakeAdapter(AIProviderErrorCode errorCode, int failuresBeforeSuccess) {
            super(new AIProviderRetryPolicy());
            this.errorCode = errorCode;
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public ProviderType providerType() {
            return ProviderType.OPENAI_CHAT_COMPLETIONS;
        }

        @Override
        protected AIProviderResult doGenerate(AIProviderRequest request, ApiConfig config) {
            attempts++;
            if (attempts <= failuresBeforeSuccess) {
                throw new AIProviderException(providerType(), errorCode, "failed", null);
            }
            return new AIProviderResult("ok", null, null, config.getModelName(), null, null, null, 1L, providerType());
        }
    }
}
