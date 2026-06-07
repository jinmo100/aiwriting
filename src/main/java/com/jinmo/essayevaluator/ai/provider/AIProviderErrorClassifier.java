package com.jinmo.essayevaluator.ai.provider;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AIProviderErrorClassifier {

    public AIProviderErrorCode classify(Throwable throwable) {
        String message = collectMessages(throwable).toLowerCase(Locale.ROOT);

        if (message.contains("401") || message.contains("403")
            || message.contains("unauthorized") || message.contains("forbidden")
            || message.contains("invalid api key") || message.contains("incorrect api key")) {
            return AIProviderErrorCode.AUTH_ERROR;
        }
        if (message.contains("model not found") || message.contains("model_not_found")
            || message.contains("does not exist") || message.contains("unknown model")) {
            return AIProviderErrorCode.MODEL_NOT_FOUND;
        }
        if (message.contains("404") || message.contains("invalid url") || message.contains("not found")) {
            return AIProviderErrorCode.INVALID_BASE_URL;
        }
        if (message.contains("429") || message.contains("rate limit") || message.contains("too many requests")) {
            return AIProviderErrorCode.RATE_LIMIT;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return AIProviderErrorCode.NETWORK_TIMEOUT;
        }
        if (message.contains("500") || message.contains("502") || message.contains("503") || message.contains("504")
            || message.contains("service unavailable") || message.contains("bad gateway")) {
            return AIProviderErrorCode.PROVIDER_5XX;
        }
        if (message.contains("content policy") || message.contains("safety") || message.contains("blocked")) {
            return AIProviderErrorCode.CONTENT_POLICY_BLOCKED;
        }
        if (message.contains("connection") || message.contains("network") || message.contains("connect")) {
            return AIProviderErrorCode.NETWORK_ERROR;
        }
        return AIProviderErrorCode.UNKNOWN_ERROR;
    }

    private static String collectMessages(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return builder.toString();
    }
}
