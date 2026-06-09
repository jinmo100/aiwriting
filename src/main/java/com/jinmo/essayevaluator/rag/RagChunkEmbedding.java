package com.jinmo.essayevaluator.rag;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户维度隔离的知识片段向量记录。
 *
 * <p>Java 实体不直接映射 {@code embedding_vector}，向量写入通过 mapper 的 pgvector literal 参数完成，
 * 避免误把大向量返回给前端。</p>
 */
@Data
@TableName("rag_chunk_embeddings")
public class RagChunkEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long embeddingConfigId;

    private Long chunkId;

    private String embeddingModel;

    private Integer embeddingDimension;

    private String embeddingVersion;

    private String contentHash;

    private LocalDateTime indexedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
