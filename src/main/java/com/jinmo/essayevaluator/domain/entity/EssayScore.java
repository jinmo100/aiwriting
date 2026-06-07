package com.jinmo.essayevaluator.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态 Rubric 评分结果实体。旧四维字段已废除，完整结果保存在 resultJson。
 */
@Data
@TableName("essay_scores")
public class EssayScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long essayId;

    private Long apiConfigId;

    private String scoringStatus;

    private String rubricType;

    private String rubricVersion;

    private Double nativeScore;

    private String nativeScoreDisplay;

    private Double normalizedScore;

    private String gradeLabel;

    private String confidenceLevel;

    private String resultJson;

    private String aiModel;

    private Integer tokensUsed;

    private Integer processingTime;

    private String errorCode;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
