package com.jinmo.aiwriting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.aiwriting.ai.provider.AIProviderErrorCode;
import com.jinmo.aiwriting.ai.provider.AIProviderException;
import com.jinmo.aiwriting.ai.provider.AIProviderRequest;
import com.jinmo.aiwriting.ai.provider.AIProviderResult;
import com.jinmo.aiwriting.ai.provider.ProviderAdapterRegistry;
import com.jinmo.aiwriting.ai.provider.ProviderEndpointResolver;
import com.jinmo.aiwriting.ai.provider.ProviderType;
import com.jinmo.aiwriting.common.exception.BusinessException;
import com.jinmo.aiwriting.domain.dto.ProviderTestRequest;
import com.jinmo.aiwriting.domain.dto.ProviderTestResponse;
import com.jinmo.aiwriting.domain.entity.ApiConfig;
import com.jinmo.aiwriting.mapper.ApiConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProviderTestService {

    private final ApiConfigMapper apiConfigMapper;
    private final ProviderAdapterRegistry providerAdapterRegistry;
    private final ProviderEndpointResolver endpointResolver;
    private final ObjectMapper objectMapper;

    public ProviderTestResponse testConnection(ProviderTestRequest request) {
        ApiConfig config = toTemporaryConfig(request);
        return testConnection(config, false);
    }

    public ProviderTestResponse testConnection(Long configId) {
        return testConnection(loadConfig(configId), true);
    }

    public ProviderTestResponse testStructuredOutput(ProviderTestRequest request) {
        ApiConfig config = toTemporaryConfig(request);
        return testStructuredOutput(config, false);
    }

    public ProviderTestResponse testStructuredOutput(Long configId) {
        return testStructuredOutput(loadConfig(configId), true);
    }

    private ProviderTestResponse testConnection(ApiConfig config, boolean persistSummary) {
        long start = System.currentTimeMillis();
        ProviderType providerType = effectiveProviderType(config);
        try {
            AIProviderResult result = providerAdapterRegistry.get(providerType).generate(
                new AIProviderRequest(null, "Reply with exactly: OK", null, null, null),
                config
            );
            long latency = result.latencyMillis() != null ? result.latencyMillis() : System.currentTimeMillis() - start;
            ProviderTestResponse response = ProviderTestResponse.success(providerType, config.getModelName(), latency, "连接成功");
            if (persistSummary) {
                saveSummary(config, response);
            }
            return response;
        } catch (Exception e) {
            ProviderTestResponse response = failure(providerType, config.getModelName(), System.currentTimeMillis() - start, e);
            if (persistSummary) {
                saveSummary(config, response);
            }
            return response;
        }
    }

    private ProviderTestResponse testStructuredOutput(ApiConfig config, boolean persistSummary) {
        long start = System.currentTimeMillis();
        ProviderType providerType = effectiveProviderType(config);
        try {
            AIProviderResult result = providerAdapterRegistry.get(providerType).generate(
                new AIProviderRequest(
                    null,
                    "Return only JSON exactly matching this data: {\"status\":\"ok\",\"score\":1}",
                    "ProviderStructuredOutputTest",
                    """
                        {
                          "type": "object",
                          "required": ["status", "score"],
                          "properties": {
                            "status": {"type": "string"},
                            "score": {"type": "number"}
                          }
                        }
                        """,
                    null
                ),
                config
            );
            validateStructuredTestJson(result.text());
            long latency = result.latencyMillis() != null ? result.latencyMillis() : System.currentTimeMillis() - start;
            ProviderTestResponse response = ProviderTestResponse.structuredSuccess(providerType, config.getModelName(), latency);
            if (persistSummary) {
                saveSummary(config, response);
            }
            return response;
        } catch (Exception e) {
            ProviderTestResponse response = failure(providerType, config.getModelName(), System.currentTimeMillis() - start, e);
            if (persistSummary) {
                saveSummary(config, response);
            }
            return response;
        }
    }

    private void validateStructuredTestJson(String text) throws Exception {
        String json = text == null ? "" : text
            .replaceAll("```json\\s*", "")
            .replaceAll("```\\s*$", "")
            .trim();
        JsonNode root = objectMapper.readTree(json);
        if (!root.hasNonNull("status") || !"ok".equals(root.get("status").asText())
            || !root.hasNonNull("score") || root.get("score").asInt() != 1) {
            throw new AIProviderException(null, AIProviderErrorCode.STRUCTURED_OUTPUT_FAILED,
                "结构化输出不符合预期", null);
        }
    }

    private ApiConfig loadConfig(Long configId) {
        ApiConfig config = apiConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException("配置不存在");
        }
        return config;
    }

    private ApiConfig toTemporaryConfig(ProviderTestRequest request) {
        ApiConfig config = new ApiConfig();
        config.setConfigName("temporary-test");
        config.setProviderType(request.providerType());
        config.setProviderLabel(request.providerLabel());
        config.setBaseUrl(endpointResolver.normalizeBaseUrl(request.providerType(), request.baseUrl()));
        config.setApiKey(request.apiKey());
        config.setModelName(request.modelName());
        config.setTemperature(request.temperature() != null ? request.temperature() : 0.3);
        config.setMaxTokens(request.maxTokens() != null ? request.maxTokens() : 256);
        config.setTimeoutSeconds(request.timeoutSeconds() != null ? request.timeoutSeconds() : 30);
        config.setModelParametersJson(request.modelParametersJson());
        return config;
    }

    private ProviderType effectiveProviderType(ApiConfig config) {
        return config.getProviderType() != null ? config.getProviderType() : ProviderType.OPENAI_CHAT_COMPLETIONS;
    }

    private ProviderTestResponse failure(ProviderType providerType, String modelName, long latency, Exception e) {
        AIProviderErrorCode code = e instanceof AIProviderException providerException
            ? providerException.getErrorCode()
            : AIProviderErrorCode.UNKNOWN_ERROR;
        String message = e instanceof AIProviderException providerException
            ? providerException.getSafeMessage()
            : "Provider 测试失败: " + e.getMessage();
        return ProviderTestResponse.failure(providerType, modelName, latency, message, code);
    }

    private void saveSummary(ApiConfig config, ProviderTestResponse response) {
        ApiConfig update = new ApiConfig();
        update.setId(config.getId());
        update.setLastTestStatus(Boolean.TRUE.equals(response.success()) ? "SUCCESS" : "FAILED");
        update.setLastTestErrorCode(response.errorCode() != null ? response.errorCode().name() : null);
        update.setLastTestMessage(response.message());
        update.setLastTestLatencyMs(response.latencyMillis() != null ? response.latencyMillis().intValue() : null);
        update.setLastTestedAt(LocalDateTime.now());
        apiConfigMapper.updateById(update);
    }
}
