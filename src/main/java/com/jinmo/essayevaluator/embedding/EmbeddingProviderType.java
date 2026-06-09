package com.jinmo.essayevaluator.embedding;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Embedding Provider 协议类型。
 *
 * <p>V1 只支持 OpenAI-compatible `/v1/embeddings`，后续新增供应商时再扩展枚举，避免前端传任意字符串。</p>
 */
public enum EmbeddingProviderType {
    OPENAI_EMBEDDINGS("OPENAI_EMBEDDINGS");

    @EnumValue
    @JsonValue
    private final String value;

    EmbeddingProviderType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static EmbeddingProviderType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Embedding Provider 类型不能为空");
        }
        for (EmbeddingProviderType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim()) || type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的 Embedding Provider 类型: " + value);
    }
}
