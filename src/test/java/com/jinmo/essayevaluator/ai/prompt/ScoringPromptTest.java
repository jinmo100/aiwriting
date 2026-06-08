package com.jinmo.essayevaluator.ai.prompt;

import com.jinmo.essayevaluator.ai.RubricTestFixtures;
import com.jinmo.essayevaluator.domain.enums.EssayType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringPromptTest {

    @Test
    void promptAndSchemaRequestAnnotationQuoteAndReferenceEssay() {
        String userPrompt = ScoringPrompt.buildUserPrompt(
            EssayType.GENERAL,
            "",
            "This is a sample essay.",
            RubricTestFixtures.generalRubric()
        );
        String schema = ScoringPrompt.scoringSchema();

        assertThat(userPrompt).contains("quote");
        assertThat(userPrompt).contains("referenceEssay");
        assertThat(schema).contains("\"quote\"");
        assertThat(schema).contains("\"referenceEssay\"");
        assertThat(schema).contains("\"content\"");
        assertThat(schema).contains("\"notes\"");
    }
}
