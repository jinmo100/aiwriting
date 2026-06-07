package com.jinmo.essayevaluator.ai.provider;

import org.springframework.stereotype.Component;

@Component
public class GeminiGenerateContentAdapter extends LangChainAIProviderAdapter {

    public GeminiGenerateContentAdapter(
        LangChainChatModelFactory chatModelFactory,
        AIProviderErrorClassifier errorClassifier,
        AIProviderRetryPolicy retryPolicy,
        LangChainResponseFormatFactory responseFormatFactory
    ) {
        super(chatModelFactory, errorClassifier, retryPolicy, responseFormatFactory);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.GEMINI_GENERATE_CONTENT;
    }
}
