package com.jinmo.aiwriting.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.ResponseFormat;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static org.assertj.core.api.Assertions.assertThat;

class LangChainResponseFormatFactoryTest {

    private final LangChainResponseFormatFactory factory = new LangChainResponseFormatFactory(new ObjectMapper());

    @Test
    void buildsJsonSchemaResponseFormatFromObjectSchema() {
        ResponseFormat format = factory.fromSchema("TestSchema", """
            {"type":"object","required":["status"],"properties":{"status":{"type":"string"},"score":{"type":"number"},"items":{"type":"array","items":{"type":"string"}}}}
            """);

        assertThat(format.type()).isEqualTo(JSON);
        assertThat(format.jsonSchema()).isNotNull();
        assertThat(format.jsonSchema().name()).isEqualTo("TestSchema");
    }

    @Test
    void fallsBackToJsonModeForInvalidSchema() {
        ResponseFormat format = factory.fromSchema("Broken", "not-json");

        assertThat(format).isEqualTo(ResponseFormat.JSON);
    }
}
