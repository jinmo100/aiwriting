package com.jinmo.essayevaluator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinmo.essayevaluator.rag.RagFeedbackCitation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * RAG Feedback 引用 Mapper。
 */
@Mapper
public interface RagFeedbackCitationMapper extends BaseMapper<RagFeedbackCitation> {

    @Select("""
        SELECT *
        FROM rag_feedback_citations
        WHERE feedback_id = #{feedbackId}
        ORDER BY rank_no ASC
        """)
    List<RagFeedbackCitation> findByFeedbackId(@Param("feedbackId") Long feedbackId);
}
