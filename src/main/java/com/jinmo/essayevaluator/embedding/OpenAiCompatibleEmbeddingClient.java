package com.jinmo.essayevaluator.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinmo.essayevaluator.ai.provider.AIProviderErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OpenAI-compatible `/v1/embeddings` 客户端。
 *
 * <p>V1 固定校验 1536 维，防止不同模型维度混入 pgvector `vector(1536)` 表导致运行期写入失败。</p>
 */
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final int SUPPORTED_DIMENSIONS = 1536;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiCompatibleEmbeddingClient(ObjectMapper objectMapper) {
        this(HttpClient.newHttpClient(), objectMapper);
    }

    OpenAiCompatibleEmbeddingClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public EmbeddingResult embed(EmbeddingConfig config, String apiKey, List<String> input) {
        validateRequest(config, apiKey, input);
        long start = System.currentTimeMillis();
        try {
            String requestJson = buildRequestJson(config, input);
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveEmbeddingsEndpoint(config.getBaseUrl())))
                .timeout(Duration.ofSeconds(effectiveTimeoutSeconds(config)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerError(response.statusCode(), response.body(), null);
            }
            return parseResponse(response.body(), input.size(), config, System.currentTimeMillis() - start);
        } catch (EmbeddingClientException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            throw new EmbeddingClientException(
                AIProviderErrorCode.INVALID_BASE_URL,
                "Embedding Provider 地址无效",
                error
            );
        } catch (Exception error) {
            throw new EmbeddingClientException(
                classifyMessage(error.getMessage()),
                "Embedding Provider 调用失败，请检查网络、地址和模型配置",
                error
            );
        }
    }

    public static String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Embedding baseUrl 不能为空");
        }
        String normalized = trimTrailingSlashes(baseUrl.trim());
        if (normalized.endsWith("/embeddings")) {
            normalized = trimTrailingSlashes(normalized.substring(0, normalized.length() - "/embeddings".length()));
        }
        if (!normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return normalized;
    }

    public static String resolveEmbeddingsEndpoint(String baseUrl) {
        return normalizeBaseUrl(baseUrl) + "/embeddings";
    }

    private void validateRequest(EmbeddingConfig config, String apiKey, List<String> input) {
        if (config == null) {
            throw new EmbeddingClientException(AIProviderErrorCode.INVALID_PROVIDER_CONFIG, "Embedding 配置不能为空", null);
        }
        if (config.getProviderType() != EmbeddingProviderType.OPENAI_EMBEDDINGS) {
            throw new EmbeddingClientException(AIProviderErrorCode.INVALID_PROVIDER_CONFIG, "仅支持 OpenAI-compatible Embedding", null);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new EmbeddingClientException(AIProviderErrorCode.AUTH_ERROR, "Embedding API Key 不能为空", null);
        }
        if (config.getDimensions() == null || config.getDimensions() != SUPPORTED_DIMENSIONS) {
            throw new EmbeddingClientException(
                AIProviderErrorCode.INVALID_PROVIDER_CONFIG,
                "V1 仅支持 1536 维 Embedding",
                null
            );
        }
        if (input == null || input.isEmpty()) {
            throw new EmbeddingClientException(AIProviderErrorCode.INVALID_PROVIDER_CONFIG, "Embedding 输入不能为空", null);
        }
    }

    private String buildRequestJson(EmbeddingConfig config, List<String> input) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getModelName());
        ArrayNode inputArray = root.putArray("input");
        for (String item : input) {
            inputArray.add(item == null ? "" : item);
        }
        root.put("dimensions", SUPPORTED_DIMENSIONS);
        return objectMapper.writeValueAsString(root);
    }

    private EmbeddingResult parseResponse(String body, int expectedCount, EmbeddingConfig config, long latencyMillis) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.get("data");
        if (data == null || !data.isArray() || data.size() != expectedCount) {
            throw new EmbeddingClientException(
                AIProviderErrorCode.INVALID_PROVIDER_CONFIG,
                "Embedding Provider 返回结构不符合预期",
                null
            );
        }

        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new EmbeddingClientException(
                    AIProviderErrorCode.INVALID_PROVIDER_CONFIG,
                    "Embedding Provider 未返回向量",
                    null
                );
            }
            if (embedding.size() != SUPPORTED_DIMENSIONS) {
                throw new EmbeddingClientException(
                    AIProviderErrorCode.INVALID_PROVIDER_CONFIG,
                    "Embedding Provider 返回的向量维度不是 1536",
                    null
                );
            }
            List<Double> vector = new ArrayList<>(SUPPORTED_DIMENSIONS);
            for (JsonNode value : embedding) {
                vector.add(value.asDouble());
            }
            embeddings.add(vector);
        }

        String model = root.hasNonNull("model") ? root.get("model").asText() : config.getModelName();
        return new EmbeddingResult(embeddings, latencyMillis, model, SUPPORTED_DIMENSIONS);
    }

    private EmbeddingClientException providerError(int statusCode, String body, Throwable cause) {
        AIProviderErrorCode code = switch (statusCode) {
            case 401, 403 -> AIProviderErrorCode.AUTH_ERROR;
            case 404 -> classifyMessage(body) == AIProviderErrorCode.MODEL_NOT_FOUND
                ? AIProviderErrorCode.MODEL_NOT_FOUND
                : AIProviderErrorCode.INVALID_BASE_URL;
            case 429 -> AIProviderErrorCode.RATE_LIMIT;
            case 500, 502, 503, 504 -> AIProviderErrorCode.PROVIDER_5XX;
            default -> classifyMessage(body);
        };
        return new EmbeddingClientException(code, safeMessageFor(code), cause);
    }

    private static AIProviderErrorCode classifyMessage(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("401") || lower.contains("403") || lower.contains("unauthorized")
            || lower.contains("forbidden") || lower.contains("invalid api key")) {
            return AIProviderErrorCode.AUTH_ERROR;
        }
        if (lower.contains("model not found") || lower.contains("model_not_found") || lower.contains("unknown model")) {
            return AIProviderErrorCode.MODEL_NOT_FOUND;
        }
        if (lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests")) {
            return AIProviderErrorCode.RATE_LIMIT;
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return AIProviderErrorCode.NETWORK_TIMEOUT;
        }
        if (lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504")) {
            return AIProviderErrorCode.PROVIDER_5XX;
        }
        if (lower.contains("connection") || lower.contains("network") || lower.contains("connect")) {
            return AIProviderErrorCode.NETWORK_ERROR;
        }
        return AIProviderErrorCode.UNKNOWN_ERROR;
    }

    private static String safeMessageFor(AIProviderErrorCode code) {
        return switch (code) {
            case AUTH_ERROR -> "Embedding Provider 认证失败，请检查 API Key";
            case MODEL_NOT_FOUND -> "Embedding 模型不存在或不可用";
            case INVALID_BASE_URL -> "Embedding Provider 地址无效";
            case RATE_LIMIT -> "Embedding Provider 请求过于频繁，请稍后重试";
            case NETWORK_TIMEOUT -> "Embedding Provider 请求超时";
            case PROVIDER_5XX -> "Embedding Provider 服务暂时不可用";
            default -> "Embedding Provider 调用失败";
        };
    }

    private static int effectiveTimeoutSeconds(EmbeddingConfig config) {
        return config.getTimeoutSeconds() != null && config.getTimeoutSeconds() > 0
            ? config.getTimeoutSeconds()
            : DEFAULT_TIMEOUT_SECONDS;
    }

    private static String trimTrailingSlashes(String value) {
        return value.replaceAll("/+$", "");
    }
}
