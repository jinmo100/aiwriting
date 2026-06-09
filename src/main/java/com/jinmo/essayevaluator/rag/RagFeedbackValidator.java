package com.jinmo.essayevaluator.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinmo.essayevaluator.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RAG Feedback JSON 校验器。
 *
 * <p>LLM 输出必须先通过结构和数量约束校验，才允许保存到业务表并展示给用户。</p>
 */
@Component
public class RagFeedbackValidator {

    private final ObjectMapper objectMapper;

    public RagFeedbackValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidatedFeedback validate(String json) {
        try {
            ValidatedFeedback feedback = objectMapper.readValue(stripFence(json), ValidatedFeedback.class);
            validateFeedback(feedback);
            return feedback;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException("RAG Feedback JSON 解析失败");
        }
    }

    private void validateFeedback(ValidatedFeedback feedback) {
        if (feedback == null || !StringUtils.hasText(feedback.overall())) {
            throw new BusinessException("RAG Feedback 缺少 overall");
        }
        if (feedback.items() == null || feedback.items().isEmpty() || feedback.items().size() > 5) {
            throw new BusinessException("RAG Feedback items 数量必须为 1 到 5");
        }
        for (FeedbackItem item : feedback.items()) {
            if (item == null || item.citationIds() == null || item.citationIds().isEmpty()) {
                throw new BusinessException("RAG Feedback item 必须包含 citationIds");
            }
            if (!StringUtils.hasText(item.title())
                || !StringUtils.hasText(item.problem())
                || !StringUtils.hasText(item.whyItMatters())
                || !StringUtils.hasText(item.howToImprove())) {
                throw new BusinessException("RAG Feedback item 字段不完整");
            }
        }
        if (feedback.nextPractice() == null || feedback.nextPractice().isEmpty() || feedback.nextPractice().size() > 3
            || feedback.nextPractice().stream().anyMatch(item -> !StringUtils.hasText(item))) {
            throw new BusinessException("RAG Feedback nextPractice 数量必须为 1 到 3");
        }
    }

    private String stripFence(String json) {
        if (json == null) {
            return "";
        }
        return json
            .replaceAll("(?s)^```json\\s*", "")
            .replaceAll("(?s)^```\\s*", "")
            .replaceAll("(?s)```\\s*$", "")
            .trim();
    }

    public record ValidatedFeedback(
        String overall,
        List<FeedbackItem> items,
        List<String> nextPractice
    ) {
    }

    public record FeedbackItem(
        String title,
        String problem,
        String whyItMatters,
        String howToImprove,
        FeedbackExample example,
        List<Long> citationIds
    ) {
    }

    public record FeedbackExample(
        String before,
        String after
    ) {
    }
}
