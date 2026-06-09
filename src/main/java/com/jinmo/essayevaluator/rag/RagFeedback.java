package com.jinmo.essayevaluator.rag;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 教学反馈结果。
 */
@Data
@TableName("rag_feedbacks")
public class RagFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long essayId;

    private Long scoreId;

    private Long apiConfigId;

    private Long embeddingConfigId;

    private Long jobId;

    private String queryText;

    private String retrievedChunkIds;

    private String feedbackJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
