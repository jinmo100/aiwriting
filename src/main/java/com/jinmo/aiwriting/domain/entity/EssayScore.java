package com.jinmo.aiwriting.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 作文评分结果实体
 */
@Data
@TableName("essay_scores")
public class EssayScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long essayId;

    private Long apiConfigId;

    // 各维度分数
    private Double overallScore;

    private Double contentScore;

    private Double languageScore;

    private Double structureScore;

    private Double coherenceScore;

    // 详细反馈（JSON格式）
    private String strengths;

    private String suggestions;

    private String errors;

    private String detailedFeedback;

    // 元数据
    private String aiModel;

    private Integer tokensUsed;

    private Integer processingTime;  // 处理耗时(ms)

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
