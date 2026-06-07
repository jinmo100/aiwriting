package com.jinmo.essayevaluator.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作文提交实体。正文和任务要求均视为不可信用户输入。
 */
@Data
@TableName("essays")
public class Essay {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String essayType;

    private String taskPrompt;

    private String content;

    private Integer wordCount;

    private Integer charCount;

    private String inputAnalysisJson;

    private String safetyAnalysisJson;

    private String idempotencyKey;

    private String contentHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
