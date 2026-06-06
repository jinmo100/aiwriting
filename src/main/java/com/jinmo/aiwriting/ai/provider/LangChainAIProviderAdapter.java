package com.jinmo.aiwriting.ai.provider;

import com.jinmo.aiwriting.domain.entity.ApiConfig;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;

public abstract class LangChainAIProviderAdapter extends RetryingAIProviderAdapter {

    private final LangChainChatModelFactory chatModelFactory;
    private final AIProviderErrorClassifier errorClassifier;
    private final LangChainResponseFormatFactory responseFormatFactory;

    protected LangChainAIProviderAdapter(
        LangChainChatModelFactory chatModelFactory,
        AIProviderErrorClassifier errorClassifier,
        AIProviderRetryPolicy retryPolicy,
        LangChainResponseFormatFactory responseFormatFactory
    ) {
        super(retryPolicy);
        this.chatModelFactory = chatModelFactory;
        this.errorClassifier = errorClassifier;
        this.responseFormatFactory = responseFormatFactory;
    }

    @Override
    protected AIProviderResult doGenerate(AIProviderRequest request, ApiConfig config) {
        long start = System.currentTimeMillis();
        try {
            ChatModel chatModel = chatModelFactory.create(config);
            ChatResponse response = chatModel.chat(buildChatRequest(request));
            long latency = System.currentTimeMillis() - start;
            TokenUsage tokenUsage = response.tokenUsage();
            return new AIProviderResult(
                response.aiMessage() != null ? response.aiMessage().text() : "",
                response.toString(),
                response.id(),
                response.modelName() != null ? response.modelName() : config.getModelName(),
                tokenUsage != null ? tokenUsage.inputTokenCount() : null,
                tokenUsage != null ? tokenUsage.outputTokenCount() : null,
                tokenUsage != null ? tokenUsage.totalTokenCount() : null,
                latency,
                providerType()
            );
        } catch (AIProviderException e) {
            throw e;
        } catch (Exception e) {
            AIProviderErrorCode code = errorClassifier.classify(e);
            throw new AIProviderException(providerType(), code, "AI Provider 调用失败: " + code, e);
        }
    }

    private ChatRequest buildChatRequest(AIProviderRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(SystemMessage.from(request.systemPrompt()));
        }
        messages.add(UserMessage.from(request.userPrompt()));

        ChatRequest.Builder builder = ChatRequest.builder().messages(messages);
        if (request.responseSchemaJson() != null && !request.responseSchemaJson().isBlank()) {
            builder.responseFormat(responseFormatFactory.fromSchema(request.responseSchemaName(), request.responseSchemaJson()));
        }
        return builder.build();
    }
}
