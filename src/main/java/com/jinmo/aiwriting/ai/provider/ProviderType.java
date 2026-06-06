package com.jinmo.aiwriting.ai.provider;

/**
 * AI Provider 协议适配器类型。
 *
 * <p>这里表示调用协议，而不是品牌名称。</p>
 */
import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProviderType {
    OPENAI_CHAT_COMPLETIONS("OPENAI_CHAT_COMPLETIONS"),
    OPENAI_RESPONSES("OPENAI_RESPONSES"),
    ANTHROPIC_MESSAGES("ANTHROPIC_MESSAGES"),
    GEMINI_GENERATE_CONTENT("GEMINI_GENERATE_CONTENT");

    @EnumValue
    @JsonValue
    private final String value;

    ProviderType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
