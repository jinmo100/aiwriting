package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.rag.RagFeedback;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * RAG Feedback Mapper。
 */
@Mapper
public interface RagFeedbackMapper extends BaseMapper<RagFeedback> {

    @Select("""
        SELECT *
        FROM rag_feedbacks
        WHERE user_id = #{userId}
          AND essay_id = #{essayId}
        ORDER BY created_at DESC
        LIMIT 1
        """)
    RagFeedback findLatestForUserEssay(@Param("userId") Long userId, @Param("essayId") Long essayId);

    @Select("""
        SELECT *
        FROM rag_feedbacks
        WHERE user_id = #{userId}
          AND score_id = #{scoreId}
          AND embedding_config_id = #{embeddingConfigId}
        ORDER BY created_at DESC
        LIMIT 1
        """)
    RagFeedback findByScoreAndConfig(
        @Param("userId") Long userId,
        @Param("scoreId") Long scoreId,
        @Param("embeddingConfigId") Long embeddingConfigId
    );

    @Delete("""
        DELETE FROM rag_feedbacks
        WHERE user_id = #{userId}
          AND score_id = #{scoreId}
          AND embedding_config_id = #{embeddingConfigId}
        """)
    int deleteByScoreAndConfig(
        @Param("userId") Long userId,
        @Param("scoreId") Long scoreId,
        @Param("embeddingConfigId") Long embeddingConfigId
    );
}
