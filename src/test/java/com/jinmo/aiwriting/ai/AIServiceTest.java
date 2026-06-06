package com.jinmo.aiwriting.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIServiceTest {

    @Test
    void extractTextFromResponsesApiOutputText() throws Exception {
        String response = """
            {
              "id": "resp_123",
              "output_text": "{\\"overallScore\\":88,\\"contentScore\\":27,\\"languageScore\\":27,\\"structureScore\\":18,\\"coherenceScore\\":16,\\"strengths\\":[\\"clear argument\\"],\\"suggestions\\":[\\"add examples\\"],\\"errors\\":[],\\"detailedFeedback\\":\\"Good work.\\"}"
            }
            """;

        String extracted = AIService.extractResponseText(new ObjectMapper(), response);

        assertThat(extracted).contains("\"overallScore\":88");
        assertThat(extracted).contains("\"detailedFeedback\":\"Good work.\"");
    }
}
