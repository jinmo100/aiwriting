package com.jinmo.essayevaluator.rag;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG Feedback 引用展示记录。
 */
@Data
@TableName("rag_feedback_citations")
public class RagFeedbackCitation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long feedbackId;

    private Long chunkId;

    private String sourceTitle;

    private String sourceType;

    private String snippet;

    private Double relevanceScore;

    private Integer rankNo;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
