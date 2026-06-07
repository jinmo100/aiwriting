package com.jinmo.essayevaluator.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class LangChainChatModelFactory {

    private final ApiKeyResolver apiKeyResolver;
    private final ObjectMapper objectMapper;
    private final ProviderAdvancedParameterMapper advancedParameterMapper;

    private final Cache<ChatModelCacheKey, ChatModel> cache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build();

    /**
     * 二级索引：配置 ID -> 该配置在当前 JVM 内创建过的模型指纹。
     *
     * <p>Caffeine 主 key 仍然是完整配置指纹，避免仅按 configId 缓存导致配置更新后复用旧模型。
     * 该索引只用于收到本地/Redis 失效事件时清理当前 JVM 已知的旧指纹。</p>
     */
    private final ConcurrentHashMap<Long, Set<ChatModelCacheKey>> configKeyIndex = new ConcurrentHashMap<>();

    public ChatModel create(ApiConfig config) {
        String apiKey = apiKeyResolver.resolve(config);
        ChatModelCacheKey key = cacheKey(config, apiKey);
        indexKey(config.getId(), key);
        return cache.get(key, ignored -> buildModel(config, apiKey));
    }

    public void invalidate(ApiConfig config) {
        if (config == null) {
            return;
        }
        try {
            cache.invalidate(cacheKey(config, apiKeyResolver.resolve(config)));
        } catch (Exception ignored) {
            // 失效失败不应阻塞配置更新；TTL 会兜底。
        }
        invalidateByConfigId(config.getId());
    }

    public void invalidateByConfigId(Long configId) {
        if (configId == null) {
            return;
        }
        Set<ChatModelCacheKey> keys = configKeyIndex.remove(configId);
        if (keys != null && !keys.isEmpty()) {
            cache.invalidateAll(keys);
        }
    }

    private void indexKey(Long configId, ChatModelCacheKey key) {
        if (configId == null) {
            return;
        }
        configKeyIndex
            .computeIfAbsent(configId, ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
    }

    private ChatModel buildModel(ApiConfig config, String apiKey) {
        ProviderType providerType = effectiveProviderType(config);
        String baseUrl = trimTrailingSlashes(config.getBaseUrl());
        String modelName = config.getModelName();
        Double temperature = defaultTemperature(config.getTemperature());
        Integer maxTokens = defaultMaxTokens(config.getMaxTokens());
        Duration timeout = Duration.ofSeconds(defaultTimeoutSeconds(config.getTimeoutSeconds()));
        var advanced = advancedParameterMapper.map(providerType, config.getModelParametersJson());

        return switch (providerType) {
            case OPENAI_CHAT_COMPLETIONS -> {
                var builder = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .maxRetries(0)
                .logRequests(false)
                    .logResponses(false);
                applyOpenAiChatAdvanced(builder, advanced);
                yield builder.build();
            }
            case OPENAI_RESPONSES -> {
                var builder = OpenAiOfficialResponsesChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxOutputTokens(maxTokens)
                .timeout(timeout)
                    .maxRetries(0);
                applyOpenAiResponsesAdvanced(builder, advanced);
                yield builder.build();
            }
            case ANTHROPIC_MESSAGES -> {
                var builder = AnthropicChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .maxRetries(0)
                .logRequests(false)
                    .logResponses(false);
                applyAnthropicAdvanced(builder, advanced);
                yield builder.build();
            }
            case GEMINI_GENERATE_CONTENT -> {
                var builder = GoogleAiGeminiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxOutputTokens(maxTokens)
                .timeout(timeout)
                .maxRetries(0)
                .logRequests(false)
                    .logResponses(false);
                applyGeminiAdvanced(builder, advanced);
                yield builder.build();
            }
        };
    }

    private void applyOpenAiChatAdvanced(OpenAiChatModel.OpenAiChatModelBuilder builder, java.util.Map<String, Object> advanced) {
        if (advanced.get("top_p") instanceof Number value) builder.topP(value.doubleValue());
        if (advanced.get("presence_penalty") instanceof Number value) builder.presencePenalty(value.doubleValue());
        if (advanced.get("frequency_penalty") instanceof Number value) builder.frequencyPenalty(value.doubleValue());
        if (advanced.get("reasoning_effort") instanceof String value) builder.reasoningEffort(value);
        if (advanced.get("seed") instanceof Number value) builder.seed(value.intValue());
    }

    private void applyOpenAiResponsesAdvanced(OpenAiOfficialResponsesChatModel.Builder builder, java.util.Map<String, Object> advanced) {
        if (advanced.get("top_p") instanceof Number value) builder.topP(value.doubleValue());
        if (advanced.get("text_verbosity") instanceof String value) builder.textVerbosity(value);
        if (advanced.get("store") instanceof Boolean value) builder.store(value);
    }

    private void applyAnthropicAdvanced(AnthropicChatModel.AnthropicChatModelBuilder builder, java.util.Map<String, Object> advanced) {
        if (advanced.get("top_p") instanceof Number value) builder.topP(value.doubleValue());
        if (advanced.get("top_k") instanceof Number value) builder.topK(value.intValue());
        if (advanced.get("thinking_budget_tokens") instanceof Number value) builder.thinkingBudgetTokens(value.intValue());
        if (advanced.get("thinking_type") instanceof String value) builder.thinkingType(value);
    }

    private void applyGeminiAdvanced(GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder, java.util.Map<String, Object> advanced) {
        if (advanced.get("top_p") instanceof Number value) builder.topP(value.doubleValue());
        if (advanced.get("top_k") instanceof Number value) builder.topK(value.intValue());
        if (advanced.get("seed") instanceof Number value) builder.seed(value.intValue());
    }

    private ChatModelCacheKey cacheKey(ApiConfig config, String apiKey) {
        return new ChatModelCacheKey(
            effectiveProviderType(config),
            trimTrailingSlashes(config.getBaseUrl()),
            config.getModelName(),
            sha256(apiKey),
            defaultTemperature(config.getTemperature()),
            defaultMaxTokens(config.getMaxTokens()),
            defaultTimeoutSeconds(config.getTimeoutSeconds()),
            sha256(normalizeJson(config.getModelParametersJson()))
        );
    }

    private ProviderType effectiveProviderType(ApiConfig config) {
        return config.getProviderType() != null ? config.getProviderType() : ProviderType.OPENAI_CHAT_COMPLETIONS;
    }

    private String normalizeJson(String json) {
        if (!StringUtils.hasText(json)) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(json));
        } catch (Exception e) {
            return json.trim();
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("计算配置指纹失败", e);
        }
    }

    private static String trimTrailingSlashes(String value) {
        return value == null ? null : value.trim().replaceAll("/+$", "");
    }

    private static Double defaultTemperature(Double temperature) {
        return temperature != null ? temperature : 0.3;
    }

    private static Integer defaultMaxTokens(Integer maxTokens) {
        return maxTokens != null ? maxTokens : 2048;
    }

    private static Integer defaultTimeoutSeconds(Integer timeoutSeconds) {
        return timeoutSeconds != null ? timeoutSeconds : 60;
    }
}
