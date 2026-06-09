package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.rag.RagChunkEmbedding;
import com.jinmo.essayevaluator.rag.RagRetrievedChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * RAG 知识片段向量 Mapper。
 */
@Mapper
public interface RagChunkEmbeddingMapper extends BaseMapper<RagChunkEmbedding> {

    /**
     * 使用参数化 pgvector literal 写入向量，调用方必须先校验为固定 1536 维数字列表。
     */
    @Insert("""
        INSERT INTO rag_chunk_embeddings
          (user_id, embedding_config_id, chunk_id, embedding_model, embedding_dimension,
           embedding_version, content_hash, embedding_vector)
        VALUES
          (#{userId}, #{embeddingConfigId}, #{chunkId}, #{embeddingModel}, #{embeddingDimension},
           #{embeddingVersion}, #{contentHash}, CAST(#{embeddingVectorLiteral} AS vector))
        ON CONFLICT (user_id, embedding_config_id, chunk_id, embedding_version)
        DO UPDATE SET content_hash = EXCLUDED.content_hash,
                      embedding_model = EXCLUDED.embedding_model,
                      embedding_dimension = EXCLUDED.embedding_dimension,
                      embedding_vector = EXCLUDED.embedding_vector,
                      updated_at = CURRENT_TIMESTAMP,
                      indexed_at = CURRENT_TIMESTAMP
        """)
    int upsertEmbedding(
        @Param("userId") Long userId,
        @Param("embeddingConfigId") Long embeddingConfigId,
        @Param("chunkId") Long chunkId,
        @Param("embeddingModel") String embeddingModel,
        @Param("embeddingDimension") Integer embeddingDimension,
        @Param("embeddingVersion") String embeddingVersion,
        @Param("contentHash") String contentHash,
        @Param("embeddingVectorLiteral") String embeddingVectorLiteral
    );

    @Delete("""
        DELETE FROM rag_chunk_embeddings
        WHERE user_id = #{userId}
          AND embedding_config_id = #{embeddingConfigId}
          AND embedding_version = #{embeddingVersion}
        """)
    int deleteByUserConfigVersion(
        @Param("userId") Long userId,
        @Param("embeddingConfigId") Long embeddingConfigId,
        @Param("embeddingVersion") String embeddingVersion
    );

    @Select("""
        SELECT COUNT(*)
        FROM rag_chunk_embeddings
        WHERE user_id = #{userId}
          AND embedding_config_id = #{embeddingConfigId}
          AND embedding_version = #{embeddingVersion}
        """)
    int countByUserConfigVersion(
        @Param("userId") Long userId,
        @Param("embeddingConfigId") Long embeddingConfigId,
        @Param("embeddingVersion") String embeddingVersion
    );

    @Select("""
        SELECT
            c.id AS chunk_id,
            COALESCE(d.source_title, d.title) AS source_title,
            d.document_type AS source_type,
            c.content AS snippet,
            (e.embedding_vector <=> CAST(#{queryVectorLiteral} AS vector)) AS distance
        FROM rag_chunk_embeddings e
        JOIN rag_chunks c ON c.id = e.chunk_id
        JOIN rag_documents d ON d.id = c.document_id
        WHERE e.user_id = #{userId}
          AND e.embedding_config_id = #{embeddingConfigId}
          AND e.embedding_version = #{embeddingVersion}
          AND c.is_active = TRUE
          AND d.is_active = TRUE
          AND (d.essay_type IS NULL OR d.essay_type = #{essayType})
        ORDER BY e.embedding_vector <=> CAST(#{queryVectorLiteral} AS vector)
        LIMIT #{topK}
        """)
    java.util.List<RagRetrievedChunk> searchTopK(
        @Param("userId") Long userId,
        @Param("embeddingConfigId") Long embeddingConfigId,
        @Param("embeddingVersion") String embeddingVersion,
        @Param("essayType") String essayType,
        @Param("queryVectorLiteral") String queryVectorLiteral,
        @Param("topK") int topK
    );
}
