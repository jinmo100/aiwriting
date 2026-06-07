package com.jinmo.essayevaluator.ai.provider;

import org.springframework.stereotype.Component;

@Component
public class OpenAiChatCompletionsAdapter extends LangChainAIProviderAdapter {

    public OpenAiChatCompletionsAdapter(
        LangChainChatModelFactory chatModelFactory,
        AIProviderErrorClassifier errorClassifier,
        AIProviderRetryPolicy retryPolicy,
        LangChainResponseFormatFactory responseFormatFactory
    ) {
        super(chatModelFactory, errorClassifier, retryPolicy, responseFormatFactory);
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.OPENAI_CHAT_COMPLETIONS;
    }
}
