package com.jinmo.aiwriting.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LangChainResponseFormatFactory {

    private final ObjectMapper objectMapper;

    public ResponseFormat fromSchema(String schemaName, String schemaJson) {
        if (!StringUtils.hasText(schemaJson)) {
            return ResponseFormat.JSON;
        }
        try {
            JsonNode root = objectMapper.readTree(schemaJson);
            JsonSchema jsonSchema = JsonSchema.builder()
                .name(StringUtils.hasText(schemaName) ? schemaName : "StructuredOutput")
                .rootElement(toElement(root))
                .build();
            return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(jsonSchema)
                .build();
        } catch (Exception e) {
            log.warn("构建 LangChain4j JSON Schema 失败，回退 JSON mode: {}", e.getMessage());
            return ResponseFormat.JSON;
        }
    }

    private JsonSchemaElement toElement(JsonNode node) {
        String type = node.path("type").asText("object");
        return switch (type) {
            case "object" -> toObjectSchema(node);
            case "array" -> JsonArraySchema.builder()
                .items(toElement(node.path("items").isMissingNode() ? objectNode("string") : node.path("items")))
                .build();
            case "number" -> new JsonNumberSchema();
            case "integer" -> new JsonIntegerSchema();
            case "boolean" -> new JsonBooleanSchema();
            case "string" -> new JsonStringSchema();
            default -> new JsonStringSchema();
        };
    }

    private JsonObjectSchema toObjectSchema(JsonNode node) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        JsonNode properties = node.path("properties");
        if (properties.isObject()) {
            Iterator<String> names = properties.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                builder.addProperty(name, toElement(properties.get(name)));
            }
        }
        JsonNode required = node.path("required");
        if (required.isArray()) {
            List<String> requiredFields = new ArrayList<>();
            required.forEach(item -> requiredFields.add(item.asText()));
            builder.required(requiredFields);
        }
        builder.additionalProperties(false);
        return builder.build();
    }

    private JsonNode objectNode(String type) {
        return objectMapper.createObjectNode().put("type", type);
    }
}
