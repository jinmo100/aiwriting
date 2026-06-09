package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.rag.RagChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RAG 知识片段 Mapper。
 */
@Mapper
public interface RagChunkMapper extends BaseMapper<RagChunk> {

    /**
     * 索引只读取启用的文档和启用的 chunk，保证停用知识卡不会继续进入新索引版本。
     */
    @Select("""
        SELECT c.*
        FROM rag_chunks c
        JOIN rag_documents d ON d.id = c.document_id
        WHERE c.is_active = TRUE
          AND d.is_active = TRUE
        ORDER BY d.id ASC, c.chunk_no ASC
        """)
    List<RagChunk> findActiveChunks();
}
