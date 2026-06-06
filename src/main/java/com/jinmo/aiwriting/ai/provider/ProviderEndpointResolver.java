package com.jinmo.aiwriting.ai.provider;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 统一处理 Provider base_url 规范化与 endpoint 拼接。
 */
@Component
public class ProviderEndpointResolver {

    public String normalizeBaseUrl(ProviderType providerType, String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("base_url 不能为空");
        }

        String normalized = trimTrailingSlashes(baseUrl.trim());
        return switch (providerType) {
            case OPENAI_CHAT_COMPLETIONS -> removeSuffix(normalized, "/chat/completions");
            case OPENAI_RESPONSES -> removeSuffix(normalized, "/responses");
            case ANTHROPIC_MESSAGES -> removeSuffix(normalized, "/messages");
            case GEMINI_GENERATE_CONTENT -> removeGeminiGenerateContentPath(normalized);
        };
    }

    public String resolveGenerateEndpoint(ProviderType providerType, String baseUrl) {
        return resolveGenerateEndpoint(providerType, baseUrl, null);
    }

    public String resolveGenerateEndpoint(ProviderType providerType, String baseUrl, String modelName) {
        String normalized = normalizeBaseUrl(providerType, baseUrl);
        return switch (providerType) {
            case OPENAI_CHAT_COMPLETIONS -> normalized + "/chat/completions";
            case OPENAI_RESPONSES -> normalized + "/responses";
            case ANTHROPIC_MESSAGES -> normalized + "/messages";
            case GEMINI_GENERATE_CONTENT -> {
                if (!StringUtils.hasText(modelName)) {
                    throw new IllegalArgumentException("Gemini endpoint 需要 model_name");
                }
                yield normalized + "/models/" + modelName.trim() + ":generateContent";
            }
        };
    }

    public String resolveModelsEndpoint(ProviderType providerType, String baseUrl) {
        return normalizeBaseUrl(providerType, baseUrl) + "/models";
    }

    private static String trimTrailingSlashes(String value) {
        return value.replaceAll("/+$", "");
    }

    private static String removeSuffix(String value, String suffix) {
        if (value.endsWith(suffix)) {
            return trimTrailingSlashes(value.substring(0, value.length() - suffix.length()));
        }
        return value;
    }

    private static String removeGeminiGenerateContentPath(String value) {
        int modelsIndex = value.indexOf("/models/");
        if (modelsIndex >= 0 && value.endsWith(":generateContent")) {
            return trimTrailingSlashes(value.substring(0, modelsIndex));
        }
        return value;
    }
}
