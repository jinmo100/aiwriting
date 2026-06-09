package com.jinmo.essayevaluator.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagFeedbackValidatorTest {

    private final RagFeedbackValidator validator = new RagFeedbackValidator(new ObjectMapper());

    @Test
    void acceptsValidFeedbackWithCitations() {
        RagFeedbackValidator.ValidatedFeedback feedback = validator.validate(validJson());

        assertThat(feedback.overall()).contains("整体");
        assertThat(feedback.items()).hasSize(1);
        assertThat(feedback.items().getFirst().citationIds()).containsExactly(1L);
        assertThat(feedback.nextPractice()).containsExactly("练习改写一个主题句");
    }

    @Test
    void rejectsMissingOverall() {
        assertThatThrownBy(() -> validator.validate("""
            {"items":[],"nextPractice":["练习"]}
            """))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("overall");
    }

    @Test
    void rejectsMoreThanFiveItems() {
        String item = """
            {"title":"t","problem":"p","whyItMatters":"w","howToImprove":"h","example":{"before":"b","after":"a"},"citationIds":[1]}
            """;
        assertThatThrownBy(() -> validator.validate("""
            {"overall":"ok","items":[%s,%s,%s,%s,%s,%s],"nextPractice":["练习"]}
            """.formatted(item, item, item, item, item, item)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("items");
    }

    @Test
    void rejectsItemWithoutCitation() {
        assertThatThrownBy(() -> validator.validate("""
            {"overall":"ok","items":[{"title":"t","problem":"p","whyItMatters":"w","howToImprove":"h","example":{"before":"b","after":"a"},"citationIds":[]}],"nextPractice":["练习"]}
            """))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("citation");
    }

    @Test
    void rejectsEmptyNextPractice() {
        assertThatThrownBy(() -> validator.validate("""
            {"overall":"ok","items":[{"title":"t","problem":"p","whyItMatters":"w","howToImprove":"h","example":{"before":"b","after":"a"},"citationIds":[1]}],"nextPractice":[]}
            """))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("nextPractice");
    }

    private String validJson() {
        return """
            {
              "overall": "整体反馈",
              "items": [
                {
                  "title": "主题句更清晰",
                  "problem": "段落中心不明确",
                  "whyItMatters": "读者难以判断论点",
                  "howToImprove": "先写主题句再展开例子",
                  "example": {"before": "I like it.", "after": "This activity helped me become more responsible."},
                  "citationIds": [1]
                }
              ],
              "nextPractice": ["练习改写一个主题句"]
            }
            """;
    }
}
