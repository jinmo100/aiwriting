package com.jinmo.essayevaluator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.ai.provider.AIProviderErrorCode;
import com.jinmo.essayevaluator.ai.provider.AIProviderErrorClassifier;
import com.jinmo.essayevaluator.ai.provider.AIProviderException;
import com.jinmo.essayevaluator.ai.provider.ApiKeyResolver;
import com.jinmo.essayevaluator.ai.provider.ProviderEndpointResolver;
import com.jinmo.essayevaluator.ai.provider.ProviderModelCacheKeyBuilder;
import com.jinmo.essayevaluator.ai.provider.ProviderModelInfo;
import com.jinmo.essayevaluator.ai.provider.ProviderModelListParser;
import com.jinmo.essayevaluator.ai.provider.ProviderType;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import com.jinmo.essayevaluator.domain.dto.ProviderModelsFetchRequest;
import com.jinmo.essayevaluator.domain.dto.ProviderModelsResponse;
import com.jinmo.essayevaluator.domain.entity.ApiConfig;
import com.jinmo.essayevaluator.mapper.ApiConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderModelService {

    private static final Duration MODEL_LIST_TTL = Duration.ofMinutes(10);

    private final ApiConfigMapper apiConfigMapper;
    private final ApiKeyResolver apiKeyResolver;
    private final ProviderEndpointResolver endpointResolver;
    private final ProviderModelListParser modelListParser;
    private final ProviderModelCacheKeyBuilder cacheKeyBuilder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AIProviderErrorClassifier errorClassifier;

    public ProviderModelsResponse fetchModels(ProviderModelsFetchRequest request) {
        String baseUrl = endpointResolver.normalizeBaseUrl(request.providerType(), request.baseUrl());
        return fetchModels(request.providerType(), baseUrl, request.apiKey().trim(), Boolean.TRUE.equals(request.forceRefresh()));
    }

    public ProviderModelsResponse fetchModels(Long configId, boolean forceRefresh) {
        ApiConfig config = apiConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }
        ProviderType providerType = config.getProviderType() != null
            ? config.getProviderType()
            : ProviderType.OPENAI_CHAT_COMPLETIONS;
        String apiKey = apiKeyResolver.resolve(config);
        String baseUrl = endpointResolver.normalizeBaseUrl(providerType, config.getBaseUrl());
        return fetchModels(providerType, baseUrl, apiKey, forceRefresh);
    }

    private ProviderModelsResponse fetchModels(ProviderType providerType, String baseUrl, String apiKey, boolean forceRefresh) {
        String cacheKey = cacheKeyBuilder.build(providerType, baseUrl, apiKey);
        long start = System.currentTimeMillis();

        if (!forceRefresh) {
            List<ProviderModelInfo> cached = readCache(cacheKey);
            if (cached != null) {
                return new ProviderModelsResponse(true, true, cached, System.currentTimeMillis() - start);
            }
        }

        String rawJson = requestModels(providerType, baseUrl, apiKey);
        List<ProviderModelInfo> models = modelListParser.parse(providerType, rawJson);
        writeCache(cacheKey, models);
        return new ProviderModelsResponse(true, false, models, System.currentTimeMillis() - start);
    }

    private String requestModels(ProviderType providerType, String baseUrl, String apiKey) {
        String modelsEndpoint = endpointResolver.resolveModelsEndpoint(providerType, baseUrl);
        try {
            RestClient.RequestHeadersSpec<?> request = RestClient.create()
                .get()
                .uri(resolveModelsUri(providerType, modelsEndpoint, apiKey))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

            if (providerType == ProviderType.ANTHROPIC_MESSAGES) {
                request = request
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
            } else if (providerType != ProviderType.GEMINI_GENERATE_CONTENT) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }

            return request.retrieve().body(String.class);
        } catch (Exception e) {
            throw classify(providerType, e);
        }
    }

    private String resolveModelsUri(ProviderType providerType, String modelsEndpoint, String apiKey) {
        if (providerType != ProviderType.GEMINI_GENERATE_CONTENT) {
            return modelsEndpoint;
        }
        String separator = modelsEndpoint.contains("?") ? "&" : "?";
        return modelsEndpoint + separator + "key=" + apiKey;
    }

    private List<ProviderModelInfo> readCache(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(json)) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCache(String cacheKey, List<ProviderModelInfo> models) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(models), MODEL_LIST_TTL);
        } catch (Exception ignored) {
            // Redis 只是缓存；写入失败不影响模型列表获取。
        }
    }

    private AIProviderException classify(ProviderType providerType, Exception e) {
        AIProviderErrorCode code = errorClassifier.classify(e);
        return new AIProviderException(providerType, code, "获取模型列表失败: " + code, e);
    }
}
