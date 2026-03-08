package com.jinmo.aiwriting.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 作文实体
 */
@Data
@TableName("essays")
public class Essay {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String content;

    private Integer wordCount;

    private String essayType;  // IELTS, TOEFL, CET4, CET6

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
