package com.jinmo.essayevaluator.rag;

import com.jinmo.essayevaluator.job.BackgroundJobStatus;

import java.time.LocalDateTime;

/**
 * RAG 索引接口 DTO。
 */
public final class RagIndexDtos {

    private RagIndexDtos() {
    }

    public record RagIndexRebuildRequest(
        Long embeddingConfigId,
        Boolean force
    ) {
    }

    public record AdminRagIndexRebuildRequest(
        Long userId,
        Long embeddingConfigId,
        Boolean force
    ) {
    }

    public record RagIndexStatusResponse(
        Boolean configured,
        Long ownerUserId,
        Long embeddingConfigId,
        String embeddingVersion,
        Integer indexedChunks,
        Long jobId,
        BackgroundJobStatus status,
        String resultJson,
        String errorCode,
        String errorMessage,
        LocalDateTime updatedAt,
        String message
    ) {
    }
}
