package com.jinmo.aiwriting.domain.dto;

import com.jinmo.aiwriting.domain.entity.Essay;
import java.time.LocalDateTime;

public record EssayResponse(
    Long id,
    String content,
    Integer score,
    String strengths,
    String suggestions,
    LocalDateTime createdAt
) {
    public static EssayResponse fromEntity(Essay essay) {
        return new EssayResponse(
            essay.getId(),
            essay.getContent(),
            essay.getScore(),
            essay.getStrengths(),
            essay.getSuggestions(),
            essay.getCreatedAt()
        );
    }
} 