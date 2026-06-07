package com.jinmo.essayevaluator.ai.provider;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AIProviderRetryPolicy {

    private static final Set<AIProviderErrorCode> RETRYABLE = Set.of(
        AIProviderErrorCode.NETWORK_TIMEOUT,
        AIProviderErrorCode.NETWORK_ERROR,
        AIProviderErrorCode.PROVIDER_5XX,
        AIProviderErrorCode.RATE_LIMIT
    );

    public boolean isRetryable(AIProviderErrorCode errorCode) {
        return RETRYABLE.contains(errorCode);
    }

    public int maxAttempts(AIProviderErrorCode errorCode) {
        return isRetryable(errorCode) ? 3 : 1;
    }

    public long backoffMillis(AIProviderErrorCode errorCode, int attemptIndex) {
        if (!isRetryable(errorCode)) {
            return 0;
        }
        long first = errorCode == AIProviderErrorCode.RATE_LIMIT ? 2000 : 1000;
        return first * attemptIndex;
    }
}
