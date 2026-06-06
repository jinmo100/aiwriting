package com.jinmo.aiwriting.ai.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIProviderErrorClassifierTest {

    private final AIProviderErrorClassifier classifier = new AIProviderErrorClassifier();

    @Test
    void classifiesAuthErrors() {
        assertThat(classifier.classify(new RuntimeException("401 Unauthorized invalid api key")))
            .isEqualTo(AIProviderErrorCode.AUTH_ERROR);
        assertThat(classifier.classify(new RuntimeException("403 Forbidden")))
            .isEqualTo(AIProviderErrorCode.AUTH_ERROR);
    }

    @Test
    void classifiesModelAndBaseUrlErrors() {
        assertThat(classifier.classify(new RuntimeException("model not found")))
            .isEqualTo(AIProviderErrorCode.MODEL_NOT_FOUND);
        assertThat(classifier.classify(new RuntimeException("404 Not Found")))
            .isEqualTo(AIProviderErrorCode.INVALID_BASE_URL);
    }

    @Test
    void classifiesTransientErrors() {
        assertThat(classifier.classify(new RuntimeException("429 rate limit")))
            .isEqualTo(AIProviderErrorCode.RATE_LIMIT);
        assertThat(classifier.classify(new RuntimeException("Read timed out")))
            .isEqualTo(AIProviderErrorCode.NETWORK_TIMEOUT);
        assertThat(classifier.classify(new RuntimeException("503 Service Unavailable")))
            .isEqualTo(AIProviderErrorCode.PROVIDER_5XX);
    }
}
