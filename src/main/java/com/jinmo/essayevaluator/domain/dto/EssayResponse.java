package com.jinmo.essayevaluator.domain.dto;

import com.jinmo.essayevaluator.domain.entity.Essay;
import com.jinmo.essayevaluator.domain.enums.EssayType;

import java.time.LocalDateTime;

public record EssayResponse(
    Long id,
    String essayType,
    String essayTypeDisplayName,
    String taskPrompt,
    String taskPromptSummary,
    String content,
    Integer wordCount,
    Integer charCount,
    LocalDateTime createdAt
) {
    public static EssayResponse fromEntity(Essay essay) {
        if (essay == null) {
            return null;
        }
        EssayType type = EssayType.fromCode(essay.getEssayType());
        return new EssayResponse(
            essay.getId(),
            essay.getEssayType(),
            type.getDisplayName(),
            essay.getTaskPrompt(),
            summarize(essay.getTaskPrompt()),
            essay.getContent(),
            essay.getWordCount(),
            essay.getCharCount(),
            essay.getCreatedAt()
        );
    }

    public static String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 60 ? compact : compact.substring(0, 60) + "...";
    }
}
