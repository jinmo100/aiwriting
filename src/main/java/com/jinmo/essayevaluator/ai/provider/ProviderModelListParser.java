package com.jinmo.essayevaluator.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProviderModelListParser {

    private final ObjectMapper objectMapper;

    public List<ProviderModelInfo> parse(ProviderType providerType, String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            return switch (providerType) {
                case GEMINI_GENERATE_CONTENT -> parseGemini(root);
                case OPENAI_CHAT_COMPLETIONS, OPENAI_RESPONSES, ANTHROPIC_MESSAGES -> parseDataArray(root);
            };
        } catch (Exception e) {
            throw new AIProviderException(providerType, AIProviderErrorCode.UNKNOWN_ERROR,
                "解析模型列表失败: " + e.getMessage(), e);
        }
    }

    private List<ProviderModelInfo> parseDataArray(JsonNode root) {
        List<ProviderModelInfo> models = new ArrayList<>();
        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                String id = text(item, "id", "name");
                if (id != null && !id.isBlank()) {
                    models.add(new ProviderModelInfo(id, text(item, "display_name", "displayName", "id"), text(item, "owned_by", "ownedBy")));
                }
            }
        }
        return models;
    }

    private List<ProviderModelInfo> parseGemini(JsonNode root) {
        List<ProviderModelInfo> models = new ArrayList<>();
        JsonNode items = root.path("models");
        if (items.isArray()) {
            for (JsonNode item : items) {
                String name = text(item, "name");
                if (name != null && !name.isBlank()) {
                    String id = name.startsWith("models/") ? name.substring("models/".length()) : name;
                    models.add(new ProviderModelInfo(id, text(item, "displayName", "display_name", "name"), null));
                }
            }
        }
        return models;
    }

    private static String text(JsonNode item, String... fields) {
        for (String field : fields) {
            if (item.hasNonNull(field)) {
                return item.get(field).asText();
            }
        }
        return null;
    }
}
