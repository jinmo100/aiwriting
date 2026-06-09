package com.jinmo.essayevaluator.rag;

import lombok.Data;

/**
 * pgvector 检索返回的安全知识片段投影。
 */
@Data
public class RagRetrievedChunk {

    private Long chunkId;

    private String sourceTitle;

    private String sourceType;

    private String snippet;

    private Double distance;

    private Integer rankNo;
}
