package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.job.BackgroundJobStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RAG Feedback API DTO。
 */
public final class RagFeedbackDtos {

    private RagFeedbackDtos() {
    }

    public record RagFeedbackGenerateRequest(
        Long embeddingConfigId
    ) {
    }

    public record RagFeedbackResponse(
        Long essayId,
        Long feedbackId,
        Long jobId,
        BackgroundJobStatus status,
        String resultJson,
        String errorCode,
        String errorMessage,
        String feedbackJson,
        List<RagFeedbackCitationResponse> citations,
        LocalDateTime updatedAt,
        String message
    ) {
    }

    public record RagFeedbackCitationResponse(
        Long id,
        Long chunkId,
        String sourceTitle,
        String sourceType,
        String snippet,
        Integer rankNo,
        String reason
    ) {
        static RagFeedbackCitationResponse from(RagFeedbackCitation citation) {
            return new RagFeedbackCitationResponse(
                citation.getId(),
                citation.getChunkId(),
                citation.getSourceTitle(),
                citation.getSourceType(),
                citation.getSnippet(),
                citation.getRankNo(),
                citation.getReason()
            );
        }
    }
}
