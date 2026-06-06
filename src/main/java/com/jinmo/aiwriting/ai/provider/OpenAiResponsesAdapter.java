package com.jinmo.aiwriting.ai.provider;

import org.springframework.stereotype.Component;

@Component
public class OpenAiResponsesAdapter extends LangChainAIProviderAdapter {

    public OpenAiResponsesAdapter(
        LangChainChatModelFactory chatModelFactory,
        AIProviderErrorClassifier errorClassifier,
        AIProviderRetryPolicy retryPolicy,
        LangChainResponseFormatFactory responseFormatFactory
    ) {
        super(chatModelFactory, errorClassifier, retryPolicy, responseFormatFactory);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.OPENAI_RESPONSES;
    }
}
