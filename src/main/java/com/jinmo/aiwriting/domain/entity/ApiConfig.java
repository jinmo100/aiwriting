package com.jinmo.aiwriting.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * API配置实体
 * 用于存储用户的AI模型API配置
 */
@Data
@TableName("api_configs")
public class ApiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configName;

    private String provider;  // openai, anthropic, openrouter, deepseek

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Boolean isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
