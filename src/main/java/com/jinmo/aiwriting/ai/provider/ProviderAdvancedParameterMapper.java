package com.jinmo.aiwriting.ai.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderAdvancedParameterMapper {

    private static final Map<ProviderType, Set<String>> WHITELISTS = Map.of(
        ProviderType.OPENAI_CHAT_COMPLETIONS, Set.of(
            "top_p", "presence_penalty", "frequency_penalty", "reasoning_effort", "seed"
        ),
        ProviderType.OPENAI_RESPONSES, Set.of(
            "top_p", "reasoning_effort", "text_verbosity", "store"
        ),
        ProviderType.ANTHROPIC_MESSAGES, Set.of(
            "top_p", "top_k", "thinking_budget_tokens", "thinking_type"
        ),
        ProviderType.GEMINI_GENERATE_CONTENT, Set.of(
            "top_p", "top_k", "seed"
        )
    );

    private final ObjectMapper objectMapper;

    public Map<String, Object> map(ProviderType providerType, String modelParametersJson) {
        if (!StringUtils.hasText(modelParametersJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(modelParametersJson, new TypeReference<>() {});
            Set<String> whitelist = WHITELISTS.getOrDefault(providerType, Set.of());
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (whitelist.contains(entry.getKey())) {
                    mapped.put(entry.getKey(), entry.getValue());
                } else {
                    log.warn("忽略当前 Provider 不支持的高级参数: providerType={}, key={}", providerType, entry.getKey());
                }
            }
            return mapped;
        } catch (Exception e) {
            log.warn("高级模型参数 JSON 解析失败，将忽略: {}", e.getMessage());
            return Map.of();
        }
    }
}
