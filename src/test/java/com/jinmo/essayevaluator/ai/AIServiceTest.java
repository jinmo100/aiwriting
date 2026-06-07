package com.jinmo.essayevaluator.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIServiceTest {

    @Test
    void extractTextFromResponsesApiOutputText() throws Exception {
        String response = """
            {
              "id": "resp_123",
              "output_text": "{\"confidence\":{\"level\":\"HIGH\",\"score\":0.9,\"reasons\":[\"clear\"],\"warnings\":[]},\"dimensions\":[{\"key\":\"content_quality\",\"score\":27,\"reason\":\"clear\",\"evidence\":[\"clear argument\"],\"improvement\":\"add examples\"}],\"annotations\":[],\"summary\":{\"strengths\":[\"clear argument\"],\"priorityImprovements\":[\"add examples\"],\"overallFeedback\":\"Good work.\"}}"
            }
            """;

        String extracted = AIService.extractResponseText(new ObjectMapper(), response);

        assertThat(extracted).contains("\"dimensions\"");
        assertThat(extracted).contains("\"overallFeedback\":\"Good work.\"");
    }
}
