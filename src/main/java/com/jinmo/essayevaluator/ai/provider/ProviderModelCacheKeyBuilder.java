package com.jinmo.essayevaluator.ai.provider;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class ProviderModelCacheKeyBuilder {

    public String build(ProviderType providerType, String baseUrl, String apiKey) {
        return "essay-evaluator:provider-models:%s:%s:%s".formatted(
            providerType.value(),
            sha256(normalizeBaseUrl(baseUrl)),
            sha256(apiKey == null ? "" : apiKey.trim())
        );
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("计算模型列表缓存 Key 失败", e);
        }
    }
}
