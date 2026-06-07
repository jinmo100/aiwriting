package com.jinmo.essayevaluator.ai.provider;

import com.jinmo.essayevaluator.domain.entity.ApiConfig;

public abstract class RetryingAIProviderAdapter implements AIProviderAdapter {

    private final AIProviderRetryPolicy retryPolicy;

    protected RetryingAIProviderAdapter(AIProviderRetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    @Override
    public final AIProviderResult generate(AIProviderRequest request, ApiConfig config) {
        int attempts = 0;
        AIProviderException lastException;
        do {
            attempts++;
            try {
                return doGenerate(request, config);
            } catch (AIProviderException e) {
                lastException = e;
                if (attempts >= retryPolicy.maxAttempts(e.getErrorCode())) {
                    throw e;
                }
                sleep(retryPolicy.backoffMillis(e.getErrorCode(), attempts));
            }
        } while (true);
    }

    protected abstract AIProviderResult doGenerate(AIProviderRequest request, ApiConfig config);

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AIProviderException(providerType(), AIProviderErrorCode.NETWORK_ERROR,
                "Provider 重试等待被中断", e);
        }
    }
}
