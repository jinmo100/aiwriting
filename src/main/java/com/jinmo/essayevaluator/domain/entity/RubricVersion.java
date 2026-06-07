package com.jinmo.essayevaluator.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rubric_versions")
public class RubricVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long profileId;
    private String version;
    private String status;
    private String nativeScale;
    private Double maxNativeScore;
    private String promptInstructions;
    private String resultSchemaVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
