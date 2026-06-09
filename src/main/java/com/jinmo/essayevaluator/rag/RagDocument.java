package com.jinmo.essayevaluator.rag;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 内置知识卡文档元数据。
 */
@Data
@TableName("rag_documents")
public class RagDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String documentType;

    private String title;

    private String sourceType;

    private String sourceTitle;

    private String essayType;

    private String skillTag;

    private String levelTag;

    private String version;

    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
