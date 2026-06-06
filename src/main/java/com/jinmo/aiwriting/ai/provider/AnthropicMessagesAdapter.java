package com.jinmo.aiwriting.ai.provider;

import org.springframework.stereotype.Component;

@Component
public class AnthropicMessagesAdapter extends LangChainAIProviderAdapter {

    public AnthropicMessagesAdapter(
        LangChainChatModelFactory chatModelFactory,
        AIProviderErrorClassifier errorClassifier,
        AIProviderRetryPolicy retryPolicy,
        LangChainResponseFormatFactory responseFormatFactory
    ) {
        super(chatModelFactory, errorClassifier, retryPolicy, responseFormatFactory);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.ANTHROPIC_MESSAGES;
    }
}
