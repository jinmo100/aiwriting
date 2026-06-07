package com.jinmo.aiwriting.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("rubric_dimensions")
public class RubricDimension {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long rubricVersionId;
    private String dimensionKey;
    private String label;
    private String description;
    private Double maxScore;
    private Double weight;
    private Integer sortOrder;
    private String levelDescriptorsJson;
}
