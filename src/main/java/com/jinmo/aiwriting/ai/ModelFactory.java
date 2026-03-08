package com.jinmo.aiwriting.ai;

import com.jinmo.aiwriting.domain.entity.ApiConfig;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI模型工厂
 * 动态创建不同的AI模型实例
 */
@Slf4j
@Component
public class ModelFactory {

    /**
     * 根据配置创建AI模型
     */
    public ChatLanguageModel createModel(ApiConfig config) {
        log.info("创建AI模型: provider={}, model={}", config.getProvider(), config.getModelName());

        return OpenAiChatModel.builder()
            .baseUrl(config.getBaseUrl())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(0.3)
            .maxTokens(2048)
            .logRequests(true)
            .logResponses(true)
            .build();
    }
}
